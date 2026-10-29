package com.memolake.application;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Bean Validation constraint for topic and memo names; delegates to {@link NameRules}. {@code null} is valid.
 */
@Documented
@Constraint(validatedBy = ValidName.Validator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidName {

    String message() default "must be " + NameRules.RULE_DESCRIPTION;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidName, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || NameRules.isValid(value);
        }
    }
}
