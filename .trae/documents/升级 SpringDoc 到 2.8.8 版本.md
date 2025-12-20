为了将 SpringDoc 升级到 2.8.8 版本（解决潜在的兼容性问题），我将执行以下操作：

1.  **修改根目录 `pom.xml`** (`niro-server/pom.xml`)：
    *   在 `<properties>` 中添加 `<springdoc.version>2.8.8</springdoc.version>`。
    *   在 `<dependencyManagement>` 中显式添加 `springdoc-openapi-starter-webmvc-ui` 的依赖声明，强制覆盖 Knife4j 内部自带的旧版本 SpringDoc。

**修改后的 pom.xml 片段示例：**
```xml
<properties>
    <!-- ... -->
    <springdoc.version>2.8.8</springdoc.version>
</properties>

<dependencyManagement>
    <dependencies>
        <!-- ... -->
        <!-- 强制升级 SpringDoc 版本 -->
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>${springdoc.version}</version>
        </dependency>
        <!-- ... -->
    </dependencies>
</dependencyManagement>
```

这样做可以确保项目使用的是您指定的 2.8.8 版本，而不是 Knife4j 默认传递进来的旧版本。