# bakery-order-management-system
Bakery Order and Custom Cake Booking Platform using React and Spring Boot

## SE1020 assignment scaffold

**Current stack:** Java 21, Spring Boot 3.5.16, Maven 3.9.11, Thymeleaf,
HTML/CSS/JavaScript, Bootstrap 5.3.8, and UTF-8 `.txt` storage.
The original description above is preserved; its React reference is historical.
This assignment uses server-rendered Thymeleaf pages.

This is one Spring Boot application, one Maven project, and one executable JAR.
The six modules are Java packages inside a modular monolith, not separate
services or Maven subprojects. No database, Docker, or CI/CD is configured.
Only the home page, module landing pages, and a shared file utility are
implemented. CRUD, authentication, booking workflows, payment processing,
invoices, reviews, and admin authorization are future team work.

### Run in IntelliJ IDEA

1. Install a **JDK 21**, then open this repository folder (the folder containing
   `pom.xml`) in IntelliJ IDEA. Import/load it as a Maven project if prompted.
2. In **File > Project Structure > Project**, select JDK **21** as the Project
   SDK and use the SDK default language level (21).
3. In **Settings > Build, Execution, Deployment > Build Tools > Maven**, select
   **Maven Wrapper** for Maven home. Set the Maven importer JDK and runner JRE
   to the Project SDK (21). Reload the Maven project and let dependencies finish
   downloading. The first build needs internet access.
4. Open `src/main/java/com/bakery/BakeryApplication.java`, then click the run
   icon beside `main`. A normal Java **Application** configuration is sufficient.
   Use `com.bakery.BakeryApplication` as the main class, Java 21 as the JRE,
   and this repository root as the **Working directory**.
5. Wait for `Started BakeryApplication`, then visit
   **http://localhost:8080**. All six links open working placeholder pages.
6. Use IntelliJ's Stop button to stop the application.

If port 8080 is occupied, add `--server.port=8081` in the run configuration's
**Program arguments**, then visit http://localhost:8081.
Do not put application arguments in VM options.

### Run from a terminal

Run commands from the repository root. A separate Maven installation is not
required: the official Maven Wrapper downloads the pinned Maven version.

Windows PowerShell:

```powershell
# Change this path if your JDK 21 is installed elsewhere.
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
.\mvnw.cmd --version
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

macOS/Linux (set `JAVA_HOME` to your installed JDK 21):

```sh
sh ./mvnw --version
sh ./mvnw clean verify
sh ./mvnw spring-boot:run
```

Using `sh ./mvnw` works even if a Windows checkout did not preserve an executable
file bit. Check that `--version` reports Java 21.
Stop a terminal run with **Ctrl+C**.

To run the packaged application after `clean verify`:

```sh
java -jar target/bakery-order-management-system-0.0.1-SNAPSHOT.jar
```

The `java` on your PATH must also be Java 21. Keep the working directory at the
repository root so the default `data` directory resolves correctly.
The external data directory is deliberately not bundled inside the JAR.

### Modules and team ownership

Assign one row to each team member. All packages are under `com.bakery`.

| Module | Package | Landing route | Controller | Sample data |
| --- | --- | --- | --- | --- |
| User Management | `user` | `/users` | `UserController.java` | `data/users.txt` |
| Bakery Product Management | `product` | `/products` | `ProductController.java` | `data/products.txt` |
| Order Management | `order` | `/orders` | `OrderController.java` | `data/orders.txt` |
| Custom Cake Booking | `cake` | `/cake-bookings` | `CakeBookingController.java` | `data/cake-bookings.txt` |
| Payment and Invoice Management | `payment` | `/payments` | `PaymentController.java` | `data/payments.txt` |
| Feedback, Review and Admin Management | `feedback` | `/feedback` | `FeedbackController.java` | `data/feedback.txt` |

Every module has the same structure:

```text
src/main/java/com/bakery/<module>/
    controller/       HTTP requests and Thymeleaf view selection
    model/            Domain objects and their state
    service/          Business rules and use cases
    repository/       Reading, parsing, and saving module records
```

Each controller currently handles only its landing page. Each module's
`model`, `service`, and `repository` package contains a `package-info.java`
file documenting its responsibility. These 18 files also preserve the otherwise
empty package directories in Git without adding dummy domain classes.

### Created files and their roles

| File or group | Purpose |
| --- | --- |
| `pom.xml` | Java 21 build, stable Spring Boot parent, Web and Thymeleaf dependencies, test dependency, and executable JAR plugin. |
| `mvnw`, `mvnw.cmd` | Official Apache Maven Wrapper launchers for Unix and Windows. |
| `.mvn/wrapper/maven-wrapper.properties` | Pins Maven 3.9.11 and identifies the script-only wrapper. |
| `.gitignore` | Excludes build output, IDE settings, logs, environment files, and local runtime data; keeps shared sample files and wrapper scripts trackable. |
| `.gitattributes` | Consistent text handling and wrapper line endings across platforms. |
| `README.md` | Setup, complete file guide, architecture, and team conventions; original introductory text retained. |
| `src/main/java/com/bakery/BakeryApplication.java` | The single application entry point; scans all packages below `com.bakery`. |
| `src/main/java/com/bakery/HomeController.java` | Maps `GET /` to the home template. |
| `src/main/java/com/bakery/<module>/controller/*Controller.java` | Six controllers listed in the module table; each selects its module's template. |
| `src/main/java/com/bakery/<module>/{model,service,repository}/package-info.java` | Eighteen package descriptions and extension points for the six team members. |
| `src/main/java/com/bakery/common/package-info.java` | Documents the purpose of shared file infrastructure. |
| `src/main/java/com/bakery/common/TextFileStorage.java` | Reusable UTF-8 line reading and writing under a configurable data directory. |
| `src/main/resources/application.properties` | Application name, port, Thymeleaf encoding, and external data directory. |
| `src/main/resources/templates/index.html` | Home page with links to all six modules. |
| `src/main/resources/templates/fragments/layout.html` | Shared head, responsive navigation, footer, and script fragments. |
| `src/main/resources/templates/<module>/index.html` | Six independent landing templates: `user`, `product`, `order`, `cake`, `payment`, and `feedback`. |
| `src/main/resources/static/css/style.css` | Small shared bakery theme and keyboard skip-link styling. |
| `src/main/resources/static/js/app.js` | Highlights the current navigation link and sets `aria-current`. |
| `src/main/resources/static/vendor/bootstrap/bootstrap.min.css` | Local Bootstrap 5.3.8 stylesheet. |
| `src/main/resources/static/vendor/bootstrap/bootstrap.bundle.min.js` | Local Bootstrap JavaScript, including responsive menu behavior and bundled Popper. |
| `src/main/resources/static/vendor/bootstrap/LICENSE` | Bootstrap's upstream MIT license. |
| `src/main/resources/static/vendor/bootstrap/README.md` | Version and sources of the vendored assets. |
| `data/README.md` | Sample format, file utility usage, and persistence boundaries. |
| `data/*.txt` | Six fictional sample files listed above; no startup seeding or automatic rewriting. |
| `src/test/java/com/bakery/BakeryApplicationTests.java` | Starts the Spring test context and renders the home page plus every module page. |
| `src/test/java/com/bakery/common/TextFileStorageTests.java` | Checks UTF-8 round trips, replacement, missing-file behavior, and filename restrictions using temporary files. |
| `target/` (generated, ignored) | Compiled classes, test reports, copied resources, and the executable JAR; created by Maven. |

Thymeleaf automatically resolves template names under `templates/` with an
`.html` suffix. Spring Boot serves files under `static/` directly.
Bootstrap is checked in locally, so page styling and navigation need no CDN
connection at runtime. No Node.js or frontend build command is needed.

### File storage

See [data/README.md](data/README.md) for the exact sample format and utility
contract. `bakery.data-directory` defaults to `data`, relative to the working
directory. To isolate future development writes from tracked examples, copy
the six samples into `data-local/` and set the environment variable
`BAKERY_DATA_DIRECTORY=data-local` in IntelliJ or your shell. That directory
is ignored by Git.

The landing pages do not read or alter the sample files. Team members will
connect repositories and services as they implement each component.

### OOP and collaboration conventions

Use this dependency flow:

```text
Controller -> Service -> Repository -> common.TextFileStorage -> data/*.txt
                 |
              Model objects
```

- **Encapsulation:** give model objects private fields and meaningful methods
  that validate changes to their state.
- **Inheritance:** introduce a base type only for a genuine "is-a" relationship,
  such as a shared payment type with distinct payment variants, if the final
  requirements justify it.
- **Polymorphism:** use interfaces or overridden behavior for those variants;
  avoid forcing inheritance into unrelated modules.
- **CRUD and file handling:** implement each module's repository and service
  later. Keep text serialization in repositories and business rules in services.
- Inject dependencies through constructors. Share identifiers across modules
  instead of having controllers directly read another module's files.
- Coordinate changes to `pom.xml`, common utilities, the shared layout, and data
  formats. Keep most work within the assigned module and its template folder.
- Use GitHub feature branches and pull requests according to your team's
  agreement. Branch changes, commits, and pushes remain manual.
- Before sharing changes, run `clean verify`. Do not commit real customer data,
  credentials, or personal payment information.

The scaffold intentionally leaves business models and CRUD to the team rather
than prescribing a completed assignment solution.

### Verification

`clean verify` compiles Java 21 source, runs seven page-rendering cases and
three file-utility tests, and packages an executable Spring Boot JAR.
The tests use temporary files and do not modify the shared sample records.
For a manual smoke check, start the app, open the home page, follow all six
navigation links, and confirm the responsive menu opens on a narrow screen.

Initial verification completed with Java 21.0.9: all 10 automated tests passed,
the executable JAR started successfully, and all seven pages plus four CSS/JS
assets returned HTTP 200. Sample data was unchanged. The temporary verification
server was stopped afterward. Generated reports and startup logs are in
the ignored `target/` directory and are removed by `clean`.

### Official references

- [Spring Boot 3.5 system requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html):
  the selected stable 3.5.16 release supports Java 21.
- [Apache Maven Wrapper](https://maven.apache.org/tools/wrapper/):
  official script-based Maven bootstrapping.
- [Bootstrap downloads](https://getbootstrap.com/docs/5.3/getting-started/download/):
  source of the Bootstrap 5.3.8 assets.
