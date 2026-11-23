package io.github.psung616.tossmcp;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.github.psung616.tossmcp.client.TossApi;
import io.github.psung616.tossmcp.client.dto.OrderCreateRequest;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.util.Set;

/**
 * ARCHITECTURE §10 규칙. 원칙(PRINCIPLES)을 코드로 강제한다 — 실패하면 규칙이 아니라 코드를 고친다.
 */
@AnalyzeClasses(packages = "io.github.psung616.tossmcp", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /** 1. stdout/stderr 직접 출력은 cli 패키지만 (serve 모드 stdout 은 MCP 전용). */
    @ArchTest
    static final ArchRule noSystemOutOutsideCli = noClasses()
            .that()
            .resideOutsideOfPackage("..cli..")
            .should()
            .accessField(System.class, "out")
            .orShould()
            .accessField(System.class, "err")
            .because("serve 모드에서 stdout 은 MCP 프로토콜 전용이다");

    /** 2. 네트워크 클라이언트 사용은 client.http 패키지만 (허용 호스트 검사를 우회하지 못하게). */
    @ArchTest
    static final ArchRule networkOnlyInClientHttp = noClasses()
            .that()
            .resideOutsideOfPackage("..client.http..")
            .should()
            .dependOnClassesThat()
            .haveNameMatching("org\\.springframework\\.web\\.client\\.RestClient(\\$.*)?")
            .orShould()
            .dependOnClassesThat()
            .resideInAPackage("java.net.http..")
            .orShould()
            .dependOnClassesThat()
            .areAssignableTo(Socket.class)
            .orShould()
            .dependOnClassesThat()
            .areAssignableTo(HttpURLConnection.class)
            .orShould()
            .callMethod(URL.class, "openConnection")
            .because("외부 통신은 AllowedHostInterceptor 가 붙은 RestClient 로만 해야 한다");

    /** 3. 도구 계층은 client 내부(http·auth)에 직접 접근하지 않는다. */
    @ArchTest
    static final ArchRule toolsUseOnlyClientFacade = noClasses()
            .that()
            .resideInAPackage("..tool..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..client.http..", "..client.auth..");

    /** 4. 돈을 다루는 패키지에 double/float 금지. */
    @ArchTest
    static final ArchRule noFloatingPointForMoney = classes()
            .that()
            .resideInAnyPackage("..client.dto..", "..money..", "..order..", "..tool..", "..command..", "..stock..")
            .should(notUseFloatingPoint());

    /** 5. 환경변수 접근은 config·secret 패키지만. */
    @ArchTest
    static final ArchRule getenvOnlyInConfigAndSecret = noClasses()
            .that()
            .resideOutsideOfPackages("..config..", "..secret..")
            .should()
            .callMethod(System.class, "getenv", String.class)
            .orShould()
            .callMethod(System.class, "getenv");

    /** 6. 텔레메트리·Actuator 금지. */
    @ArchTest
    static final ArchRule noTelemetry = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework.boot.actuate..", "io.sentry..", "io.opentelemetry..");

    /** 7. 실제 주문 API 는 order 패키지(OrderService)만 호출한다 — 미리보기·가드레일 우회 방지. */
    @ArchTest
    static final ArchRule createOrderOnlyFromOrderPackage = noClasses()
            .that()
            .resideOutsideOfPackages("..order..", "..client")
            .should()
            .callMethod(TossApi.class, "createOrder", OrderCreateRequest.class)
            .because("모든 주문은 OrderService.preview -> place 를 거쳐야 한다");

    /** 8. 명령 해석기(command)는 네트워크·채팅 라이브러리에 의존하지 않는 순수 로직이다. */
    @ArchTest
    static final ArchRule commandParserIsPure = noClasses()
            .that()
            .resideInAPackage("..command..")
            .and()
            .haveSimpleNameContaining("Parser")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..client..", "org.springframework..", "net.dv8tion..")
            .allowEmptyShould(true);

    private static ArchCondition<JavaClass> notUseFloatingPoint() {
        Set<String> banned = Set.of("double", "float", "java.lang.Double", "java.lang.Float");
        return new ArchCondition<>("not use double/float for fields, parameters or return types") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                javaClass.getFields().stream()
                        .filter(f -> banned.contains(f.getRawType().getName()))
                        .forEach(f -> events.add(SimpleConditionEvent.violated(f, f.getFullName() + " 가 부동소수점 타입")));
                javaClass.getCodeUnits().stream()
                        .filter(m -> banned.contains(m.getRawReturnType().getName())
                                || m.getRawParameterTypes().stream().anyMatch(p -> banned.contains(p.getName())))
                        .forEach(m -> events.add(SimpleConditionEvent.violated(m, m.getFullName() + " 가 부동소수점 타입 사용")));
            }
        };
    }
}
