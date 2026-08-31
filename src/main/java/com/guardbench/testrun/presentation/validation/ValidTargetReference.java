package com.guardbench.testrun.presentation.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TargetReferenceReqValidator.class)
public @interface ValidTargetReference {
    String message() default "target 종류에 맞지 않는 필드 조합입니다.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
