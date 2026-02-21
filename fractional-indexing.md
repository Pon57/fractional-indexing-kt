# FractionalIndex 仕様 (Draft v1 / Rust `fractional_index` 準拠)

## 変更方針
このドキュメントは v0（Base62 + `!` レイヤー案）から、Rust の `fractional_index` 2.0.2 の仕様に合わせた v1 に更新する。

目的:
- 手実装しやすいこと
- 文字列表現とバイト列表現で同じ順序比較ができること
- 桁伸びを抑えられること

## 参考実装と背景
- Rust crate: `jamsocket/fractional_index` (`FractionalIndex`)
- 記事の検証（2025-01-21公開）では、同じ最悪ケース操作を 60,000 回繰り返したとき:
  - JavaScript 実装: 10,003 bytes
  - Rust 実装: 942 bytes

上記の差は、Rust 実装が「Base62文字列を直接伸ばす」のではなく「バイト列 + 終端バイト」で管理していることが主因。

## スコープ
- 対象:
  - 単一ライター前提でのキー生成
  - `new_before`, `new_after`, `new_between`, `new(lower?, upper?)`
  - 文字列表現（Base64 / 16進）とバイト列表現の相互変換
- 非対象:
  - 並行更新の衝突解決（jitter / CRDT）
  - オンライン compaction / rebalancing

## データモデル
### 1) 内部表現
`FractionalIndex` は `ByteArray` で表す。終端は必ず `TERMINATOR = 0x80`。

```text
bytes = payload + [0x80]
```

制約:
- 末尾は必ず `0x80`
- 空配列は無効
- `payload` は 0 バイトでもよい（この場合のキーは `[0x80]`）

### 2) 外部文字列表現
- Base64: RFC 4648 の標準表現（例: `gA==`, `gYA=`, `gX+A`）
- Hex: 小文字16進文字列（例: `80`, `8180`, `817f80`）
- 1 byte = 2 hex chars 固定長変換（hex）
- `toString()` はデバッグ用途（公開ワイヤ形式ではない）
- `toBase64String()` / `fromBase64String()` と `toHexString()` / `fromHexString()` を公開文字列表現として使う

### 3) 比較規則
- バイト列辞書順で比較する
- Hex文字列同士の辞書順は、上記バイト列比較と一致する
- Base64文字列同士の辞書順は、上記バイト列比較と一致しない場合がある

## 公開 API（Kotlin案）
```kotlin
@JvmInline
value class FractionalIndex private constructor(val bytes: ByteArray) : Comparable<FractionalIndex> {
    fun toBase64String(): String
    fun toHexString(): String

    companion object {
        fun default(): FractionalIndex // [0x80]
        fun fromBytes(bytes: ByteArray): Result<FractionalIndex>
        fun fromBase64String(value: String): Result<FractionalIndex>
        fun fromHexString(value: String): Result<FractionalIndex>

        fun new(
            lower: FractionalIndex?,
            upper: FractionalIndex?,
        ): FractionalIndex?

        fun newBefore(index: FractionalIndex): FractionalIndex
        fun newAfter(index: FractionalIndex): FractionalIndex
        fun newBetween(left: FractionalIndex, right: FractionalIndex): FractionalIndex?
    }
}
```

`new` の意味:
- `(null, null)` -> `default()`
- `(x, null)` -> `newAfter(x)`
- `(null, y)` -> `newBefore(y)`
- `(x, y)` -> `newBetween(x, y)`

## 生成アルゴリズム（Rust実装準拠）
`new_before` / `new_after` は、終端付きバイト列を左から走査し、条件を満たした位置で「切り詰め + 1byte調整」を行う。
生成後は常に `0x80` を末尾に付ける。

戦略:
- `between(left, right)` のデフォルトは `SPREAD`
- `MINIMAL` を使いたい場合は明示的に strategy を指定する

### newBefore
擬似コード:

```text
fn new_before(bytes_with_term):
  for i in 0..len(bytes_with_term):
    if bytes[i] > 0x80:
      return unterminated(bytes[0..i])
    if bytes[i] > 0x00:
      out = bytes[0..=i]
      out[i] -= 1
      return unterminated(out)
  panic (正しい終端があれば到達しない)
```

### newAfter
擬似コード:

```text
fn new_after(bytes_with_term):
  for i in 0..len(bytes_with_term):
    if bytes[i] < 0x80:
      return unterminated(bytes[0..i])
    if bytes[i] < 0xff:
      out = bytes[0..=i]
      out[i] += 1
      return unterminated(out)
  panic (正しい終端があれば到達しない)
```

### newBetween
擬似コード（Rust `new_between` の流れをそのまま要約）:

```text
fn new_between(left, right):
  if left >= right: return null

  shorter_len = min(left.len, right.len) - 1   // 終端分を除く

  for i in 0..shorter_len:
    if left[i] < right[i] - 1:
      out = left[0..=i]
      out[i] += (right[i] - left[i]) / 2
      return unterminated(out)

    if left[i] == right[i] - 1:
      prefix = left[0..=i]
      suffix = left[i+1..]
      return unterminated(prefix + new_after(suffix))

    if left[i] > right[i]:
      return null

  if left.len < right.len:
    prefix, suffix = split(right, shorter_len + 1)
    if last(prefix) < 0x80: return null
    return unterminated(prefix + new_before(suffix))

  if left.len > right.len:
    prefix, suffix = split(left, shorter_len + 1)
    if last(prefix) >= 0x80: return null
    return unterminated(prefix + new_after(suffix))

  return null
```

## 代表例（テストベクトル）
- `default()` -> bytes `[128]` -> base64 `"gA=="`
- `default()` -> bytes `[128]` -> hex `"80"`
- `newBefore("gA==")` -> `"f4A="`
- `newAfter("gA==")` -> `"gYA="`
- `newBetween("gA==", "gYA=")` -> `"gX+A"`
- `newBetween("f4A=", "gA==")` -> `"f4GA"`

## 妥当性検証
`fromBase64String` / `fromHexString` / `fromBytes` で最低限チェック:
- 空文字は不可
- Base64 として decode 可能であること
- Hex は 16進文字のみ（`0-9a-fA-F`）かつ偶数長であること
- decode 後の配列が空でないこと
- decode 後の末尾が `0x80`

エラー分類（Rust準拠）:
- `EmptyString`
- `InvalidChars`
- `MissingTerminator`
- `InvalidBounds`（`newBetween` で `left >= right`）

## DB 保存方針
推奨は `BLOB/BYTEA` 保存（バイト列そのまま）。

文字列保存する場合:
- Base64 文字列または小文字 hex 文字列に固定
- バイナリ比較相当の照合を使用
  - PostgreSQL: `TEXT COLLATE "C"`
  - MySQL/MariaDB: `ascii_bin` か `utf8mb4_bin`
  - SQLite: `COLLATE BINARY`

## テスト要件
1. 基本性質:
   - `newBefore(x) < x`
   - `x < newAfter(x)`
   - `left < newBetween(left, right) < right`
2. 文字列 API 整合:
   - `toString()` はデバッグ出力であり、公開 wire 形式は `toBase64String()` / `toHexString()` を使う
3. 変換往復:
   - `fromBase64String(toBase64String(x)) == x`
   - `fromHexString(toHexString(x)) == x`
4. 境界:
   - `newBetween(x, x) == null`
   - `newBetween(right, left) == null`
5. 長期挿入:
   - 同一区間への連続挿入で順序不変・例外なし

## KMP 実装注意
- Kotlin の `Byte` は符号付き（`-128..127`）なので、比較時は必ず unsigned として扱う。
  - 例: `val u = b.toInt() and 0xFF`
- `TERMINATOR` は `0x80` 固定。比較ロジックは常に `Int(0..255)` で実装する。
- Base64 変換は `commonMain` の `kotlin.io.encoding.Base64` を使い、実装差異を避ける。
- Hex 変換は lower-case 正規化し、parse は case-insensitive にする。

## 運用ポリシー（再採番）
- 自動再採番は行わない。
- 再採番はユーザー明示操作（管理画面または管理 API）でのみ実行する。
- 通常運用は `newBefore/newAfter/newBetween` のみで継続する。
- 監視用途として、キー長（bytes）と最大キー長だけはメトリクス化しておく。

## v0 案（Base62 + `!`レイヤー）について
v0 は「先頭無停止」を明示的に扱いやすい利点がある一方、今回採用する Rust 準拠仕様とは互換性がない。
既存データが v0 形式なら、v1 への移行は以下のいずれかで実施する:
- 全件再採番（推奨）
- v0/v1 列を分離して段階移行
