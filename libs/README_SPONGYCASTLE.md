# SpongyCastle JARs Required for TLS 1.2 Support

To enable **TLS 1.2** support on Android 2.1 (which only supports TLS 1.0 natively), download these JARs from Maven Central and place them in the `libs/` directory:

1. **core** - Core lightweight API
   - Download: https://repo1.maven.org/maven2/com/madgag/spongycastle/core/1.58.0.0/core-1.58.0.0.jar
   - Save as: `libs/spongycastle-core-1.58.0.0.jar`

2. **prov** - JCE provider (requires core)
   - Download: https://repo1.maven.org/maven2/com/madgag/spongycastle/prov/1.58.0.0/prov-1.58.0.0.jar
   - Save as: `libs/spongycastle-prov-1.58.0.0.jar`

3. **bctls-jdk15on** - TLS implementation (requires prov and core)
   - Download: https://repo1.maven.org/maven2/com/madgag/spongycastle/bctls-jdk15on/1.58.0.0/bctls-jdk15on-1.58.0.0.jar
   - Save as: `libs/spongycastle-bctls-jdk15on-1.58.0.0.jar`

**Note**: 
- These JARs are **NOT committed to git** (they're large, ~2MB total). Each developer needs to download them manually.
- SpongyCastle 1.58.0.0 supports **TLS 1.2** (not TLS 1.3, which requires newer versions). TLS 1.2 should be sufficient for most modern APIs.
- The app requires these JARs at build time and will use BouncyCastle TLS at runtime (system TLS 1.0 fallback remains if BouncyCastle fails).

**Alternative**: If you have Maven/Gradle available, you can use:
```xml
<dependency>
    <groupId>com.madgag.spongycastle</groupId>
    <artifactId>core</artifactId>
    <version>1.58.0.0</version>
</dependency>
<dependency>
    <groupId>com.madgag.spongycastle</groupId>
    <artifactId>prov</artifactId>
    <version>1.58.0.0</version>
</dependency>
<dependency>
    <groupId>com.madgag.spongycastle</groupId>
    <artifactId>bctls-jdk15on</artifactId>
    <version>1.58.0.0</version>
</dependency>
```
