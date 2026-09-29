老版本客户端报上来的字段被静默丢掉了，某些帧还会在解码时改掉调用方内存。
aplcheck 是 Java 17 的应用层协议解码库，只用 JDK 标准库（javac/java），构建走 `scripts/build.sh`，
自检走 `scripts/check.sh`（`check/` 是固定验收程序，别改），既有用例走 `bash scripts/test.sh`。

README「对外契约」的 10 条是这份实现的唯一出处。既有用例 12/12 当前全绿，但没有覆盖
未知 flags、非法 UTF-8、重复 tag、声明顺序、无符号长度、尾部多余字节和入参只读性。

任务：修正 `src/main/java/aplcheck/` 下的实现，使固定件全过，既有用例仍全绿。

验收：
- bash scripts/build.sh 退出码 0；
- bash scripts/test.sh 全绿；
- bash scripts/check.sh 退出码 0，11 个场景全过。

约束：
1. 对外类名、方法名与签名不许改；可以新增私有方法或内部类。
2. 不改 `check/`，不许删改既有用例。
3. 不许引入第三方依赖，不许加 `pom.xml` / `build.gradle`。
4. `decode` 必须是纯函数，不得修改入参；字段与 body 的边界都要严格检查。