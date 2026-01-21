# Maven POM Audit Report
## Multi-Module Project: ingestion-service

**Date:** 2026-01-21  
**Auditor:** Senior Java Build Engineer / Maven Architect  
**Project Structure:** Root POM + 3 modules (common-module, data-ingestion-service, query-service)

---

## 1. Summary

### ✅ What's Good:
- **Correct packaging**: Root POM has `packaging=pom`, modules are correctly listed
- **Parent inheritance**: Proper use of `spring-boot-starter-parent` (3.5.9) with Java 21 compatibility
- **BOM import**: Spring Cloud dependencies BOM is correctly imported via `dependencyManagement`
- **Version properties**: Most third-party libraries have versions defined in root `properties`
- **Dependency scopes**: Correct use of `runtime` for PostgreSQL driver, `test` for test dependencies, `optional` for Lombok
- **Module structure**: Clear separation between common library and Spring Boot applications
- **Spring Boot plugins**: `spring-boot-maven-plugin` correctly configured in application modules only

### 🔥 Critical Issues to Fix:
- **Redundant version override**: `spring-boot-starter-actuator` has explicit version 3.5.9 in root `dependencyManagement` (already managed by Spring Boot parent)
- **Magic versions in modules**: `springdoc-openapi-starter-webmvc-ui` (2.8.9) and `swagger-annotations` (2.2.15) have hardcoded versions in modules
- **Missing pluginManagement**: No centralized plugin management, causing duplication across modules
- **Missing encoding properties**: No `project.build.sourceEncoding` and `project.reporting.outputEncoding` properties

### ⚠️ Major Issues:
- **Plugin configuration duplication**: `maven-compiler-plugin` annotation processor paths duplicated in all modules
- **Missing dependencyManagement entries**: `springdoc-openapi-starter-webmvc-ui` and `swagger-annotations` not managed centrally
- **Lombok version inconsistency**: Lombok version specified in annotation processor paths but should be managed via dependencyManagement

### 📝 Minor Issues:
- **Missing project encoding**: UTF-8 encoding not explicitly set in properties
- **No dependency convergence enforcement**: No maven-enforcer-plugin to detect version conflicts
- **SNAPSHOT version**: Project uses `0.0.1-SNAPSHOT` (acceptable for development, but should be removed for production)

---

## 2. Issues by Severity

### 🔴 CRITICAL

#### CRIT-1: Redundant Spring Boot Version Override
**Location:** `pom.xml` lines 39-43  
**Issue:** `spring-boot-starter-actuator` has explicit version `3.5.9` in `dependencyManagement`, but Spring Boot parent already manages all Spring Boot starters  
**Impact:** Redundant and potentially confusing; if parent version changes, this override could cause conflicts  
**Fix:** Remove the version tag from `spring-boot-starter-actuator` in `dependencyManagement`

#### CRIT-2: Magic Versions in Module Dependencies
**Location:** 
- `common-module/pom.xml` line 29: `swagger-annotations` version `2.2.15`
- `data-ingestion-service/pom.xml` line 70: `springdoc-openapi-starter-webmvc-ui` version `2.8.9`
- `query-service/pom.xml` line 70: `springdoc-openapi-starter-webmvc-ui` version `2.8.9`

**Issue:** Hardcoded versions in module dependencies violate dependency management principles  
**Impact:** 
- Difficult to upgrade versions consistently
- Risk of version drift between modules
- No single source of truth for dependency versions

**Fix:** 
- Add `swagger-annotations` and `springdoc-openapi-starter-webmvc-ui` to root `dependencyManagement` with properties
- Remove version tags from module dependencies

#### CRIT-3: Missing pluginManagement Section
**Location:** Root `pom.xml`  
**Issue:** No `pluginManagement` section, causing plugin configurations to be duplicated in each module  
**Impact:** 
- Plugin versions and configurations scattered across modules
- Difficult to maintain consistency
- Risk of configuration drift

**Fix:** Create `pluginManagement` section in root POM with:
- `maven-compiler-plugin` (with annotation processor paths)
- `maven-surefire-plugin` (for tests)
- `spring-boot-maven-plugin` (for Spring Boot apps)

### 🟠 MAJOR

#### MAJ-1: Plugin Configuration Duplication
**Location:** All module POMs (`common-module/pom.xml`, `data-ingestion-service/pom.xml`, `query-service/pom.xml`)  
**Issue:** `maven-compiler-plugin` annotation processor configuration duplicated in each module  
**Impact:** 
- Maintenance burden
- Risk of inconsistent configurations
- Violates DRY principle

**Fix:** Move to root `pluginManagement`, modules inherit configuration

#### MAJ-2: Missing Dependency Management for OpenAPI/Swagger
**Location:** Root `pom.xml`  
**Issue:** `springdoc-openapi-starter-webmvc-ui` and `swagger-annotations` not in `dependencyManagement`  
**Impact:** Cannot centrally manage versions, leading to magic versions in modules  
**Fix:** Add both to `dependencyManagement` with version properties

#### MAJ-3: Lombok Version Management Inconsistency
**Location:** Root `pom.xml` and module POMs  
**Issue:** Lombok version (`1.18.30`) is in properties but not in `dependencyManagement`; version specified in annotation processor paths  
**Impact:** 
- Lombok is managed by Spring Boot parent, but explicit version in annotation paths could conflict
- Should rely on Spring Boot's managed version or explicitly manage it

**Fix:** 
- Option A: Remove version from annotation processor paths (rely on Spring Boot parent)
- Option B: Add Lombok to `dependencyManagement` explicitly (if overriding Spring Boot's version)

### 🟡 MINOR

#### MIN-1: Missing Encoding Properties
**Location:** Root `pom.xml` properties section  
**Issue:** No `project.build.sourceEncoding` and `project.reporting.outputEncoding` properties  
**Impact:** Potential encoding issues on different platforms  
**Fix:** Add `project.build.sourceEncoding=UTF-8` and `project.reporting.outputEncoding=UTF-8`

#### MIN-2: No Dependency Convergence Enforcement
**Location:** Root `pom.xml`  
**Issue:** No `maven-enforcer-plugin` to detect version conflicts  
**Impact:** Potential runtime issues from conflicting transitive dependencies  
**Fix:** Add `maven-enforcer-plugin` with `dependencyConvergence` rule

#### MIN-3: SNAPSHOT Version in Production Context
**Location:** Root `pom.xml` line 14  
**Issue:** Version is `0.0.1-SNAPSHOT`  
**Impact:** SNAPSHOT versions are mutable and not suitable for production releases  
**Fix:** Use release versions (e.g., `1.0.0`) for production builds

---

## 3. Module-by-Module Review

### Root POM (`pom.xml`)

**Purpose:** Parent POM aggregating 3 modules, providing centralized dependency and plugin management

**Current State:**
- ✅ Correctly inherits from `spring-boot-starter-parent` 3.5.9
- ✅ Packaging is `pom`
- ✅ Modules correctly listed
- ✅ Java version 21 configured
- ✅ Spring Cloud BOM imported
- ✅ Most third-party dependencies managed

**Dependency Problems:**
1. ❌ `spring-boot-starter-actuator` has redundant version override
2. ❌ Missing `swagger-annotations` in dependencyManagement
3. ❌ Missing `springdoc-openapi-starter-webmvc-ui` in dependencyManagement
4. ❌ Lombok not explicitly in dependencyManagement (though managed by parent)

**Plugin Problems:**
1. ❌ No `pluginManagement` section
2. ❌ `maven-compiler-plugin` configured directly in `build/plugins` instead of `pluginManagement`
3. ❌ Missing `maven-surefire-plugin` configuration
4. ❌ No `spring-boot-maven-plugin` management (though used in modules)

**Property Problems:**
1. ❌ Missing `project.build.sourceEncoding`
2. ❌ Missing `project.reporting.outputEncoding`

**Suggested Fixes:**
- Remove version from `spring-boot-starter-actuator` in dependencyManagement
- Add `swagger-annotations` and `springdoc-openapi-starter-webmvc-ui` to dependencyManagement
- Create `pluginManagement` section with all plugins
- Add encoding properties
- Consider adding `maven-enforcer-plugin` for dependency convergence

---

### common-module (`common-module/pom.xml`)

**Purpose:** Shared library module containing DTOs, exceptions, and utilities (no Spring Boot runtime dependencies)

**Current State:**
- ✅ Correctly inherits from root parent
- ✅ Packaging is `jar` (library, not application)
- ✅ Dependencies are minimal and appropriate for a common library
- ✅ Test dependencies correctly scoped
- ✅ Lombok correctly marked as `optional`

**Dependency Problems:**
1. ❌ `swagger-annotations` has magic version `2.2.15` (line 29)
2. ⚠️ `spring-boot-starter-test` included (acceptable for testing, but adds Spring Boot dependencies transitively)

**Plugin Problems:**
1. ❌ `maven-compiler-plugin` configuration duplicated (should inherit from pluginManagement)
2. ❌ Lombok version hardcoded in annotation processor path (line 65)

**Suggested Fixes:**
- Remove version from `swagger-annotations` dependency (manage in root)
- Remove `maven-compiler-plugin` configuration (inherit from pluginManagement)
- Consider if `spring-boot-starter-test` is necessary or if JUnit/Mockito alone suffice

**Note:** This module correctly avoids Spring Boot runtime dependencies (no `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, etc.), which is good for a common library.

---

### data-ingestion-service (`data-ingestion-service/pom.xml`)

**Purpose:** Spring Boot application for ingesting news articles from external APIs and storing in PostgreSQL

**Current State:**
- ✅ Correctly inherits from root parent
- ✅ Packaging is `jar` (Spring Boot executable JAR)
- ✅ Dependencies appropriate for a Spring Boot web application
- ✅ PostgreSQL driver correctly scoped as `runtime`
- ✅ `spring-boot-maven-plugin` correctly configured
- ✅ Lombok correctly marked as `optional`

**Dependency Problems:**
1. ❌ `springdoc-openapi-starter-webmvc-ui` has magic version `2.8.9` (line 70)
2. ⚠️ `uuid-creator` dependency present (also in common-module) - potential duplication if common-module already provides it

**Plugin Problems:**
1. ❌ `maven-compiler-plugin` configuration duplicated (should inherit from pluginManagement)
2. ❌ MapStruct processor version hardcoded (line 89) - should use property from root

**Suggested Fixes:**
- Remove version from `springdoc-openapi-starter-webmvc-ui` (manage in root)
- Remove `maven-compiler-plugin` configuration (inherit from pluginManagement)
- Verify if `uuid-creator` is needed here or if it's already transitively available from `common-module`

**Note:** This module correctly includes `spring-boot-maven-plugin` as it's an executable Spring Boot application.

---

### query-service (`query-service/pom.xml`)

**Purpose:** Spring Boot application providing REST API for querying articles and generating summaries using OpenAI

**Current State:**
- ✅ Correctly inherits from root parent
- ✅ Packaging is `jar` (Spring Boot executable JAR)
- ✅ Dependencies appropriate for a Spring Boot web application with security
- ✅ Test dependencies correctly scoped
- ✅ `spring-boot-maven-plugin` correctly configured
- ✅ Lombok correctly marked as `optional`

**Dependency Problems:**
1. ❌ `springdoc-openapi-starter-webmvc-ui` has magic version `2.8.9` (line 70)

**Plugin Problems:**
1. ❌ `maven-compiler-plugin` configuration duplicated (should inherit from pluginManagement)
2. ❌ Lombok version hardcoded in annotation processor path (line 84)

**Suggested Fixes:**
- Remove version from `springdoc-openapi-starter-webmvc-ui` (manage in root)
- Remove `maven-compiler-plugin` configuration (inherit from pluginManagement)

**Note:** This module correctly includes `spring-boot-maven-plugin` as it's an executable Spring Boot application.

---

## 4. Proposed Final Structure

### Root POM Structure

```xml
<project>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.9</version>
    </parent>
    
    <properties>
        <java.version>21</java.version>
        <maven.compiler.release>21</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
        
        <!-- Third-party library versions -->
        <spring-cloud.version>2025.0.1</spring-cloud.version>
        <mapstruct.version>1.5.5.Final</mapstruct.version>
        <openai-java.version>0.18.0</openai-java.version>
        <commons-lang3.version>3.14.0</commons-lang3.version>
        <micrometer-tracing-bridge-otel.version>1.6.1</micrometer-tracing-bridge-otel.version>
        <lombok.version>1.18.30</lombok.version>
        <uuid-creator.version>5.3.7</uuid-creator.version>
        <swagger-annotations.version>2.2.15</swagger-annotations.version>
        <springdoc-openapi.version>2.8.9</springdoc-openapi.version>
    </properties>
    
    <dependencyManagement>
        <dependencies>
            <!-- Spring Cloud BOM -->
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            
            <!-- Third-party libraries (no version for Spring Boot starters - managed by parent) -->
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-actuator</artifactId>
                <!-- No version - managed by Spring Boot parent -->
            </dependency>
            
            <dependency>
                <groupId>io.micrometer</groupId>
                <artifactId>micrometer-tracing-bridge-otel</artifactId>
                <version>${micrometer-tracing-bridge-otel.version}</version>
            </dependency>
            
            <dependency>
                <groupId>org.mapstruct</groupId>
                <artifactId>mapstruct</artifactId>
                <version>${mapstruct.version}</version>
            </dependency>
            
            <dependency>
                <groupId>com.theokanning.openai-gpt3-java</groupId>
                <artifactId>service</artifactId>
                <version>${openai-java.version}</version>
            </dependency>
            
            <dependency>
                <groupId>org.apache.commons</groupId>
                <artifactId>commons-lang3</artifactId>
                <version>${commons-lang3.version}</version>
            </dependency>
            
            <dependency>
                <groupId>com.github.f4b6a3</groupId>
                <artifactId>uuid-creator</artifactId>
                <version>${uuid-creator.version}</version>
            </dependency>
            
            <dependency>
                <groupId>io.swagger.core.v3</groupId>
                <artifactId>swagger-annotations</artifactId>
                <version>${swagger-annotations.version}</version>
            </dependency>
            
            <dependency>
                <groupId>org.springdoc</groupId>
                <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
                <version>${springdoc-openapi.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
    
    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <configuration>
                        <annotationProcessorPaths>
                            <path>
                                <groupId>org.projectlombok</groupId>
                                <artifactId>lombok</artifactId>
                                <!-- Version inherited from Spring Boot parent -->
                            </path>
                        </annotationProcessorPaths>
                    </configuration>
                </plugin>
                
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <!-- Version and configuration inherited from Spring Boot parent -->
                </plugin>
                
                <plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                    <configuration>
                        <excludes>
                            <exclude>
                                <groupId>org.projectlombok</groupId>
                                <artifactId>lombok</artifactId>
                            </exclude>
                        </excludes>
                    </configuration>
                </plugin>
            </plugins>
        </pluginManagement>
        
        <plugins>
            <!-- Only plugins that should execute at root level -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-enforcer-plugin</artifactId>
                <version>3.4.0</version>
                <executions>
                    <execution>
                        <id>enforce</id>
                        <goals>
                            <goal>enforce</goal>
                        </goals>
                        <configuration>
                            <rules>
                                <dependencyConvergence/>
                                <requireNoRepositories>
                                    <message>No repositories should be defined in POM files</message>
                                </requireNoRepositories>
                            </rules>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

### Module POM Structure (Example: common-module)

```xml
<project>
    <parent>
        <groupId>com.tispace</groupId>
        <artifactId>ingestion-service</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>
    
    <artifactId>common-module</artifactId>
    <packaging>jar</packaging>
    
    <dependencies>
        <!-- All dependencies without versions (managed by parent) -->
        <dependency>
            <groupId>io.swagger.core.v3</groupId>
            <artifactId>swagger-annotations</artifactId>
            <!-- No version - managed in root -->
        </dependency>
        
        <!-- Other dependencies... -->
    </dependencies>
    
    <build>
        <plugins>
            <!-- Only if module-specific plugin configuration needed -->
            <!-- Otherwise inherit from pluginManagement -->
        </plugins>
    </build>
</project>
```

### Module POM Structure (Example: Spring Boot Application)

```xml
<project>
    <parent>
        <groupId>com.tispace</groupId>
        <artifactId>ingestion-service</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>
    
    <artifactId>data-ingestion-service</artifactId>
    <packaging>jar</packaging>
    
    <dependencies>
        <!-- All dependencies without versions -->
    </dependencies>
    
    <build>
        <plugins>
            <!-- Inherit compiler plugin config from pluginManagement -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <!-- Add MapStruct processor if needed -->
                <configuration>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </path>
                        <path>
                            <groupId>org.mapstruct</groupId>
                            <artifactId>mapstruct-processor</artifactId>
                            <version>${mapstruct.version}</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
            
            <!-- Inherit Spring Boot plugin config from pluginManagement -->
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## 5. Concrete Patch Plan

### File: `pom.xml` (Root)

**Changes:**

1. **Add encoding properties** (after line 28):
   ```xml
   <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
   <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
   ```

2. **Add new version properties** (after line 35):
   ```xml
   <swagger-annotations.version>2.2.15</swagger-annotations.version>
   <springdoc-openapi.version>2.8.9</springdoc-openapi.version>
   ```

3. **Remove version from spring-boot-starter-actuator** (line 42):
   - Remove: `<version>3.5.9</version>`
   - Keep dependency declaration without version

4. **Add missing dependencies to dependencyManagement** (after line 75):
   ```xml
   <dependency>
       <groupId>io.swagger.core.v3</groupId>
       <artifactId>swagger-annotations</artifactId>
       <version>${swagger-annotations.version}</version>
   </dependency>
   <dependency>
       <groupId>org.springdoc</groupId>
       <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
       <version>${springdoc-openapi.version}</version>
   </dependency>
   ```

5. **Move build/plugins to pluginManagement** (replace lines 79-94):
   - Move `maven-compiler-plugin` configuration to `pluginManagement`
   - Add `maven-surefire-plugin` to `pluginManagement` (optional, inherits from parent)
   - Add `spring-boot-maven-plugin` to `pluginManagement` with Lombok exclude configuration
   - Remove Lombok version from annotation processor path (rely on Spring Boot parent)

6. **Add maven-enforcer-plugin** (optional, in build/plugins):
   ```xml
   <plugin>
       <groupId>org.apache.maven.plugins</groupId>
       <artifactId>maven-enforcer-plugin</artifactId>
       <version>3.4.0</version>
       <executions>
           <execution>
               <id>enforce</id>
               <goals>
                   <goal>enforce</goal>
               </goals>
               <configuration>
                   <rules>
                       <dependencyConvergence/>
                   </rules>
               </configuration>
           </execution>
       </executions>
   </plugin>
   ```

---

### File: `common-module/pom.xml`

**Changes:**

1. **Remove version from swagger-annotations** (line 29):
   - Remove: `<version>2.2.15</version>`

2. **Remove maven-compiler-plugin configuration** (lines 55-69):
   - Remove entire plugin block (will inherit from pluginManagement)
   - If Lombok annotation processing is needed, it will be inherited from root pluginManagement

---

### File: `data-ingestion-service/pom.xml`

**Changes:**

1. **Remove version from springdoc-openapi-starter-webmvc-ui** (line 70):
   - Remove: `<version>2.8.9</version>`

2. **Update maven-compiler-plugin** (lines 76-93):
   - Keep plugin declaration but remove Lombok version (line 84)
   - Keep MapStruct processor path (it's module-specific)
   - Plugin configuration will partially inherit from pluginManagement
   - Note: MapStruct processor version should use `${mapstruct.version}` property (already correct on line 89)

---

### File: `query-service/pom.xml`

**Changes:**

1. **Remove version from springdoc-openapi-starter-webmvc-ui** (line 70):
   - Remove: `<version>2.8.9</version>`

2. **Update maven-compiler-plugin** (lines 76-88):
   - Remove Lombok version from annotation processor path (line 84)
   - Keep plugin declaration (will inherit Lombok configuration from pluginManagement)
   - Or remove entire plugin block if no module-specific configuration needed

---

## 6. Additional Recommendations

### Dependency Convergence Check

After applying fixes, run:
```bash
mvn dependency:tree -Dverbose
mvn enforcer:enforce
```

This will help identify any remaining version conflicts.

### Reproducible Builds

Consider adding:
```xml
<properties>
    <maven.compiler.release>21</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
</properties>
```

(Already partially implemented, encoding properties need to be added)

### SNAPSHOT Versions

For production releases:
- Change version from `0.0.1-SNAPSHOT` to `1.0.0` (or appropriate release version)
- Use Maven release plugin or CI/CD to manage version increments

### Testing Strategy

Consider:
- Using `maven-failsafe-plugin` for integration tests (separate from unit tests)
- Configuring test execution order if needed
- Adding test coverage plugins (JaCoCo) if not already present

---

## 7. Compatibility Verification

### Spring Boot 3.5.9 + Java 21
✅ **Compatible** - Spring Boot 3.x requires Java 17+, Java 21 is fully supported

### Jakarta vs javax
✅ **Consistent** - Project uses `jakarta.validation-api` (correct for Spring Boot 3.x)

### Dependency Versions
✅ **Compatible** - All managed dependencies are compatible with Spring Boot 3.5.9:
- Spring Cloud 2025.0.1 ✅
- MapStruct 1.5.5.Final ✅
- Lombok 1.18.30 ✅
- Jackson (managed by Spring Boot) ✅

### No Reactive/Servlet Mixing
✅ **Clean** - Only servlet stack (`spring-boot-starter-web`) is used, no reactive dependencies

---

## Conclusion

The project has a solid foundation with proper multi-module structure and Spring Boot parent inheritance. The main issues are:
1. Redundant version overrides
2. Magic versions in modules
3. Missing pluginManagement
4. Missing encoding properties

After applying the fixes outlined in this report, the POM structure will be production-ready with:
- Centralized dependency management
- Consistent plugin configuration
- No magic versions
- Proper encoding settings
- Dependency convergence enforcement

**Estimated effort:** 1-2 hours to apply all fixes and verify with `mvn dependency:tree` and `mvn enforcer:enforce`.

