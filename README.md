# aplcheck

Java 17 的**应用层协议解码**小库。只用 JDK 标准库（`javac` / `java`），
不用 Maven / Gradle，不引入任何第三方依赖。

```java
Schema schema = new Schema(List.of(new Schema.Field(1, "user"), new Schema.Field(2, "host")));
Msg msg = new Decoder(schema).decode(frame);
```

## 目录

```
src/main/java/aplcheck/Decoder.java     解码器（含若干没有守住契约的地方）
src/main/java/aplcheck/Schema.java      字段表（含若干没有守住契约的地方）
src/main/java/aplcheck/Msg.java         解码结果：版本 / flags / 字段
src/main/java/aplcheck/AplException.java           所有帧错误的基类
src/main/java/aplcheck/BadMagicException.java      magic 不匹配
src/main/java/aplcheck/UnsupportedVersionException.java  version 高于支持版本
src/main/java/aplcheck/TruncatedFrameException.java      帧被截断
src/main/java/aplcheck/MalformedBodyException.java       body 不是合法 UTF-8
src/main/java/aplcheck/SchemaConflictException.java      Schema 里 tag 重复
src/test/java/aplcheck/DecoderTest.java  既有用例（12 个，手写 runner，无第三方框架）
scripts/build.sh                         编译 main / test
scripts/test.sh                          跑既有用例
scripts/check.sh                         固定验收入口（勿改）
check/Checker.java                       固定验收程序（勿改）
REVIEW.md                                审查结论（待填，本任务的交付物）
```

## 语言版本前提

- Java 17（`javac` / `java`）。
- 只用 JDK 标准库；构建产物在 `out/`、`out-test/`、`out-check/`，已在 `.gitignore` 中忽略。

## 怎么跑

```bash
bash scripts/build.sh      # 编译
bash scripts/test.sh       # 既有用例（12 个）
bash scripts/check.sh      # 固定验收；支持 -list 与 --only <组名>
bash scripts/check.sh -list
bash scripts/check.sh --only finding
```

## 线格式

```
偏移 0..1   magic    2 字节   0x41 0x50（"AP"）
偏移 2      version  1 字节
偏移 3      flags    1 字节   已知位：0x01 压缩、0x02 签名
偏移 4..7   len      4 字节   little-endian，body 的字节数
偏移 8..    body             len 个字节
```

`body` 里的值是**按字段表声明顺序**依次排下来的：

```
每个字段：  len(2, LE) | 该字段的 UTF-8 值（len 个字节）
```

字段本身不带 tag —— tag 由字段表给出，所以**字段表的顺序就是解码的一部分**。

`Msg` 的对外形状：

```
aplcheck.Msg
  #version()      -> int
  #flags()        -> int           已知位（0x01 / 0x02）
  #rawFlags()     -> int           解码时读到的 flags 原始字节（含未知位）
  #tags()         -> List<Integer> 字段 tag，按解码顺序
  #field(int tag) -> String        没有这个 tag 时返回 null
  #encode()       -> byte[]        重新编码成帧
```

## 对外保证

下面 8 条是 `aplcheck` 的**对外契约**，它们是本题验收点的唯一出处。
**它们是契约，不是「当前行为」的转述**。

1. **入口与线格式**：`Decoder.decode(byte[] frame) -> Msg`，线格式见上一节。
   `magic` 不是 `0x41 0x50` 时抛 `BadMagicException`。
2. **版本**：`version` **高于**当前支持版本（1）时，必须抛 `UnsupportedVersionException`，
   **不得**静默按当前版本解析、也不得返回 `null`。
3. **长度**：`len` 是 body 的字节数。`len > frame.length - 8` 抛 `TruncatedFrameException`；
   `len < 0` 同样抛 `TruncatedFrameException`（不得溢出绕过检查）。
4. **flags 未知位**：`flags` 里**未知位必须保留**在 `Msg.rawFlags()` 里，
   并且在 `encode()` 时**原样写回**。
5. **字符串字段**：按 **UTF-8** 解码；非法字节序列必须抛 `MalformedBodyException`，
   **不得**替换成 `U+FFFD` / `?` 之后照常返回。
6. **字段表**：`Schema` 的字段解析按**声明顺序**；同一个 `tag` 声明两次时构造即抛
   `SchemaConflictException`（不得静默覆盖）。
7. **纯函数**：`decode` 对同一 `frame` 多次调用结果相同，且**不得修改入参 `byte[]`**。
8. **边界**：空数组、只有 7 字节、`len == 0` 都不得 panic，按上面的规则抛对应异常。

9. **严格尾部**：按字段表解完所有字段后，`body` 必须恰好耗尽；有剩余字节时抛
   `TruncatedFrameException`，不得静默忽略。
10. **声明顺序是线格式的一部分**：`Schema.fields()` 必须保持构造时的顺序，不能因 `HashMap`
    迭代而重排；同一 `tag` 重复声明必须在构造时抛 `SchemaConflictException`。

## 本次任务

`src/main/java/aplcheck/` 下有一版能通过既有用例、但违反上述契约的实现。请修正实现，
使其行为满足 README 的 10 条契约；不要改既有用例。

## 验收

`check/Checker.java` 是固定验收程序，**不要修改**。它按 10 组共 11 个场景检查
`basic` / `version` / `length` / `flags` / `utf8` / `schema` / `purity` / `roundtrip` / `tail`。

起点状态：`bash scripts/test.sh` 12/12 全绿；`bash scripts/check.sh` 有部分场景失败、退出码 1。