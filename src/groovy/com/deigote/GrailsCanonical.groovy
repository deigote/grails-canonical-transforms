package com.deigote

import org.codehaus.groovy.transform.GroovyASTTransformationClass
import java.lang.annotation.ElementType
import java.lang.annotation.Target
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Retention

@Retention (RetentionPolicy.RUNTIME)
@Target ([ElementType.TYPE])
@GroovyASTTransformationClass (["com.deigote.GrailsCanonicalTransformation"])
public @interface GrailsCanonical {
	String[] includes() default ['id']
   String logLevel() default ''
}