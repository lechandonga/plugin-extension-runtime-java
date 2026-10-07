# 本地插件化扩展框架（plugin-extension-runtime-java）

基于本地目录中的**扩展清单 + 实现类**模拟第三方扩展来源，提供扩展的发现、装配、配置注入、依赖解析、原子热更新与故障隔离能力。所有行为在并发与失败场景下都确定且可解释。

## 1. 目录与清单格式

配置 `extensions.root` 指向一个本地扩展根目录，其**每个一级子目录是一个扩展来源**，布局固定：

```
<extensions.root>/
  my-extension/                 # 目录名仅用于定位，扩展身份以清单 id 为准
    extension.json              # 扩展清单（必需）
    classes/                    # 实现类根目录（必需），扩展独立 URLClassLoader 加载
      com/example/MyExt.class
      MyExt.java                # 源码可保留，框架只加载 .class/.jar
    lib/                        # 可选：扩展私有依赖 jar（仅该扩展可见）
      some-lib.jar
```

`extension.json` 字段：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `id` | 是 | 扩展标识：字母/数字开头，可含 `. _ -`；同一次扫描内必须唯一 |
| `name` | 否 | 展示名，缺省取 id |
| `version` | 是 | 扩展自身版本，语义化版本，如 `1.2.0`、`1.2.3-SNAPSHOT` |
| `apiVersion` | 是 | 扩展针对的框架接口版本 |
| `className` | 是 | 实现类全限定名，必须实现 SPI `Extension` 且有无参构造方法 |
| `dependencies` | 否 | `{ "依赖id": "版本区间" }` |
| `configDefaults` | 否 | 本扩展命名空间配置的默认键值 |

示例见 `examples/extensions/`。

## 2. 扩展 SPI

扩展实现 `com.github.lechandonga.pluginextensionruntimejava.ext.spi.Extension`：

```java
public interface Extension {
    default void start(ExtensionContext context) throws Exception { } // 依赖已按拓扑序启动
    default void stop() { }                                            // 热更切换/关闭时逆序调用
    default Object invoke(String action, Object payload) { return null; }
}
```

`ExtensionContext` 提供：

- `getConfig(key[, default])`：读取**本扩展独立命名空间**的配置；
- `requireDependency(id)`：取得已启动的依赖扩展实例；缺失时立即抛错。

每个扩展使用独立的 `URLClassLoader`（父加载器为框架加载器），扩展实现类彼此不可见，从类加载层面隔离故障与版本。

## 3. 配置优先级（确定且可解释）

每个扩展的配置命名空间为 `ext.<扩展id>.<键>`。优先级**从高到低**：

1. **系统属性**：`-Dext.demo-greeter.greeting=Hi`（来源 `SYSTEM_PROPERTY`）
2. **外部覆盖文件**：`extensions.override-file` 指向的 properties 文件，内容 `ext.<id>.<key>=...`（来源 `OVERRIDE_FILE:<路径>`）
3. **清单默认值**：`extension.json` 的 `configDefaults`（来源 `MANIFEST_DEFAULT`）

解析结果可通过 `ExtensionRuntime.configOf(id)` 或 `GET /extensions/{id}/config` 查询，返回每个键的最终值与来源，优先级完全可解释；不同 id 的配置互不可见。

## 4. 依赖与版本兼容规则

### 版本号与版本区间

- 版本为语义化版本 `major.minor.patch[.qualifier]`，缺省段按 0 处理；正式版大于同号带限定符版本（如 `1.0.0 > 1.0.0-SNAPSHOT`）。
- 依赖区间为 Maven 风格：
  - `[1.0,2.0)` 左闭右开；`[1.0,2.0]`、`(1.0,2.0)` 边界开闭可控；`[1.0,)` 表示不小于 1.0；
  - 裸版本 `1.2` 等价于 `[1.2,2.0.0)`（同主版本且不低于该版本）。

### 接口（API）版本兼容

运行时支持版本由 `extensions.api-version` 配置（默认 `1.0`）。兼容条件：**主版本相同，且扩展要求的次/补丁版本不高于运行时版本**。不兼容以 `INCOMPATIBLE_API_VERSION` 拒绝。

### 装配判定（均明确拒绝，绝不静默跳过）

| 拒绝原因 | 触发条件 |
| --- | --- |
| `INVALID_MANIFEST` | 缺清单、JSON 非法、缺必填字段、版本/区间无法解析 |
| `DUPLICATE_ID` | 扫描到两个相同 `id` 的扩展（保留目录序第一个） |
| `INCOMPATIBLE_API_VERSION` | 接口版本不兼容 |
| `MISSING_DEPENDENCY` | 依赖 id 不存在，或依赖自身被拒绝（固定点向上游传播） |
| `VERSION_RANGE_CONFLICT` | 依赖实际版本不在声明区间内 |
| `CIRCULAR_DEPENDENCY` | 依赖图成环（含自依赖） |
| `CLASS_LOAD_FAILED` | 实现类未实现 SPI、找不到、无法实例化 |
| `START_FAILED` | `start(context)` 抛异常 |

装配按依赖图**拓扑排序**启动、关闭时逆序停止。任何扩展失败都不影响与其无依赖关系的扩展；依赖失败扩展的上游会连带以 `MISSING_DEPENDENCY` 拒绝，因此**不会有扩展带着半截状态启动**。

## 5. 热更新与故障隔离语义

- **发现/读路径**：注册表是不可变快照，经 `volatile` 引用整体发布；读操作彼此完全并发。
- **热更新串行化**：`reload()` 与读操作互斥（内部同一把锁），并发 reload 自动串行，不存在交错状态。
- **原子切换**：新快照只有在全部目标扩展扫描、装配、实例化、`start()` 全部成功后才发布。并发调用**只能观察到旧版本或新版本**。
- **失败回退**：若新扫描结果会让任何此前在服务扩展失效（清单/依赖/类加载/启动任一失败），或出现新的被拒扩展，本次候选整体丢弃（候选类加载器全部关闭、已启动的候选逆序停止），**旧版本原封不动继续服务**；结果中 `rolledBack=true` 且带可解释 `errorMessage`，修复后可再次热更。
- **运行期故障隔离**：`invoke` 中扩展抛出的任何异常被包装为 `ExtensionInvocationException`，只传播给当次调用方，并记录到该扩展的 `lastRuntimeError`；不摘除扩展（可继续服务），也不影响其它扩展和整体服务。
- 可选目录监听：`extensions.watch=true` 时监听根目录变化，500ms 防抖后自动调用同一套确定性 `reload()`；默认关闭。

## 6. 配置项与 HTTP 接口

`application.properties`：

```properties
extensions.root=examples/extensions
extensions.api-version=1.0
extensions.override-file=examples/override.properties
extensions.watch=false
```

接口：

- `GET  /extensions`：所有扩展的状态（`ACTIVE`/`REJECTED`）、版本、接口版本、来源目录、拒绝原因/细节、加载时间、最近运行期错误、最近热更错误；
- `GET  /extensions/{id}/config`：最终配置值与每个键的来源；
- `POST /extensions/reload`：触发热更新，返回更新/保持/移除清单与是否回退；
- `POST /extensions/{id}/invoke?action=xx`：调用扩展（body 作为 payload）。

## 7. 本地验证

前置：JDK 21+、Maven 3.9+。

### 7.1 运行单元测试

```bash
mvn test
```

共 24 个用例，按业务模块组织，日志会打印「业务模块 → 用例 → 通过/失败（耗时）」以及框架的装配/拒绝/热更决策，覆盖：

- **扩展发现与生命周期管理**（`DiscoveryAndLifecycleTest`，7 例）：正常加载、清单非法（JSON/缺字段）、id 重复、接口版本不兼容、实现类缺失、单扩展失败隔离；
- **依赖装配与版本兼容**（`DependencyAssemblyTest`，6 例）：拓扑序启动、缺失依赖、区间冲突、循环依赖、自依赖、失败向上游传播；
- **扩展配置注入与优先级**（`ConfigInjectionTest`，2 例）：三级优先级与来源可解释、命名空间隔离；
- **运行时热更新与原子切换**（`HotReloadTest`，6 例）：成功切换、启动失败回退、清单失败回退、回退后恢复、并发读只观察旧/新版本、并发 reload 串行化；
- **运行期故障隔离**（`FaultIsolationTest`，2 例）：invoke 抛错隔离且留痕、未知扩展调用；
- **Spring 集成冒烟**（`ApplicationTests`，1 例）。

### 7.2 命令行演示（不经 Spring）

```bash
mvn -q -DskipTests package

# 编译示例扩展（SPI 类在 target/classes 中）
CP="target/classes"
javac -cp "$CP" -d examples/extensions/greeter/classes  examples/extensions/greeter/classes/ext/demo/GreeterExtension.java
javac -cp "$CP" -d examples/extensions/translator/classes examples/extensions/translator/classes/ext/demo/TranslatorExtension.java

java -cp "$CP" com.github.lechandonga.pluginextensionruntimejava.ExtensionDemo \
     examples/extensions examples/override.properties

# 观察最高优先级：系统属性覆盖外部文件与清单默认值
java -Dext.demo-greeter.greeting=Bonjour -cp "$CP" \
     com.github.lechandonga.pluginextensionruntimejava.ExtensionDemo \
     examples/extensions examples/override.properties
```

### 7.3 Web 方式验证热更

```bash
mvn -q -DskipTests package
java -jar target/plugin-extension-runtime-java-1.0-SNAPSHOT.jar \
     --extensions.root="$(pwd)/examples/extensions" \
     --extensions.override-file="$(pwd)/examples/override.properties"

curl -s localhost:8080/extensions | jq
curl -s -XPOST localhost:8080/extensions/reload | jq
```

修改某个扩展目录（改 `extension.json` 的 version 或替换 `classes/` 中实现）后再次 reload：
合法变更 → 发布新版本；非法变更 → `rolledBack=true`，`GET /extensions` 中旧版本保持 `ACTIVE` 并继续服务。

## 8. 主要代码结构

```
ext/
  model/      Version, VersionRange, Manifest, ExtensionStatus, RejectReason, ExtensionStateView...
  spi/        Extension, ExtensionContext
  config/     ConfigResolver, ConfigResolution（三级优先级与来源）
  discovery/  ExtensionScanner, ManifestParser, ExtensionClassLoader, ManifestParseException
  assembly/   DependencyResolver, DependencyResolution（兼容检查、环检测、拓扑排序、拒绝传播）
  runtime/    ExtensionRuntime, ExtensionRegistry, ActiveExtension, ReloadResult, ExtensionInvocationException
  spring/     ExtensionProperties, ExtensionRuntimeConfiguration, ExtensionDirectoryWatcher
  web/        ExtensionController
```
