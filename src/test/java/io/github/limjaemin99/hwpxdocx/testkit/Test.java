package io.github.limjaemin99.hwpxdocx.testkit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** {@link TestRunner}가 실행할 시험 메서드. 매개변수 없는 인스턴스 메서드에 붙인다. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Test {
}
