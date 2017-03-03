package com.deigote

import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.syntax.SyntaxException
import org.codehaus.groovy.control.messages.SyntaxErrorMessage
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.ast.builder.AstBuilder
import java.lang.annotation.Annotation

@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
public class GrailsCanonicalTransformation implements ASTTransformation {

   private final static String configurationErrorMessage =
      "Transformation ${GrailsCanonicalTransformation.class.name} should be applied to a class annotation"
   private final static String unexpectedErrorMessage =
      "Unexpected error when applying transformation ${GrailsCanonicalTransformation.class.name} to class "
   
   void visit(ASTNode[] astNodes, SourceUnit sourceUnit) {       
      def (annotationNode, classNode) = astNodes?.size() >= 2 ? astNodes[0..1] : [null, null]
      try {
         if (annotationNode && classNode &&
            annotationNode instanceof AnnotationNode &&
            annotationNode.classNode?.name == GrailsCanonical.class.name &&
            classNode instanceof ClassNode) {
               def includes = getValueForMember(annotationNode, 'includes')
               def logLevel = getValueForMember(annotationNode, 'logLevel')?.getAt(0)
               addEqualsMethod(classNode, includes, logLevel)
               addHashCodeMethod(classNode, includes, logLevel)
               addToStringMethod(classNode, includes, logLevel)
         }
         else
         {
            addError(sourceUnit, , annotationNode)
         }
      } 
      catch (t)
      {
         addError(sourceUnit, unexpectedErrorMessage + classNode?.name, annotationNode)
         t.printStackTrace();
      }
   }

   private getValueForMember(annotationNode, member) {
      def includesExpression = annotationNode.getMember(member)
      def includes = includesExpression?.respondsTo('getExpressions') ?
           includesExpression?.getExpressions()*.value :
           includesExpression ? [includesExpression?.value] : null
   }
   
   protected void addError(sourceUnit, String msg, ASTNode expr) {
      sourceUnit.getErrorCollector().addErrorAndContinue(new SyntaxErrorMessage(
         new SyntaxException(msg + '\n', expr.getLineNumber(), expr.getColumnNumber()),
         sourceUnit)
      );
   }
   
   MethodNode addEqualsMethod(ClassNode declaringClass, List<String> includes, String logLevel) {
      addMethod(declaringClass, 'equals', includes, { className, includesString ->
         """
         boolean equals(Object obj) {
            boolean isEquals = obj != null && org.hibernate.Hibernate.getClass(obj) == org.hibernate.Hibernate.getClass(this) && (
               this.is(obj) || (this.id != null && this.id == obj.id && !this.dirty && !obj.dirty) ||
               ${includesString}.inject(true) {
                  isEqual, property ->
                  if (isEqual) {
                     def propDefaultValue = this.domainClass.associations.find { assoc ->
                        assoc.name == property && (
                           Collection.isAssignableFrom(assoc.type) || 
                           Map.isAssignableFrom(assoc.type)
                        )
                     }?.with { assoc ->
                        Collection.isAssignableFrom(assoc.type) ? [] :
                        Map.isAssignableFrom(assoc.type) ? [:] : null
                     }
                     def thisValue = this[property] == null ? propDefaultValue : this[property]
                     def objValue = obj[property] == null ? propDefaultValue : obj[property]
                     if ("${logLevel?:''}") {
                        log.$logLevel "\$this - equals - comparing \$property with values \${thisValue} and \${objValue}"
                     }
                     return isEqual && thisValue == objValue 
                  }
                  else {
                     return false
                  }
               }
            )
            if ("${logLevel?:''}") {
               log.$logLevel "\$this - equals - comparing with \${obj} - \${isEquals}"
            }
            return isEquals
         }
         """
      })
   }

   MethodNode addHashCodeMethod(ClassNode declaringClass, List<String> includes, String logLevel)
   {
      def hashCodeHelper = 'org.codehaus.groovy.util.HashCodeHelper'
      addMethod(declaringClass, 'hashCode', includes, { className, includesString ->
         """
         int hashCode() {
            (!this.id ? ${includesString} : ['domainClass', 'id']).inject(${hashCodeHelper}.initHash()) {
               currentHashCode, property ->
               if ("${logLevel?:''}") {
                  log.$logLevel "\$this - hashCode - appending \$property with value \${this[property]}"
               }
               ${hashCodeHelper}.updateHash(currentHashCode, this[property])
            }
         }
         """
      })
   }

   MethodNode addToStringMethod(ClassNode declaringClass, List<String> includes, String logLevel)
   {
      
      addMethod(declaringClass, 'toString', ['id'] + (includes - 'id'), { className, includesString ->
         """
         String toString() {
            "\${getClass().simpleName}(" + ${includesString}.collect { property ->
               "\${property}: \${this[property]}"
            }.join(', ') + ')'
         }
         """
      })
   }
   
   private String calculateIncludesAsString(List<String> includes) {
      includes.collect { "'" + it + "'" }
   }
   
   private MethodNode addMethod(ClassNode declaringClass, String methodName, List<String> includes,
      Closure methodBodyBuilder) {
      String className = declaringClass.name
      String includesString = calculateIncludesAsString(includes)
      String methodBody = methodBodyBuilder(className, includesString)
      logAddingMethod(methodName, className, includes, methodBody)
      declaringClass.addMethod(new AstBuilder().buildFromString(
         CompilePhase.INSTRUCTION_SELECTION, false, methodBody
      ).getAt(1).methods.find { it.name == methodName })
   }
   
   private logAddingMethod(methodName, className, includes, methodBody) {
      println "Creating $methodName for class $className($includes) with body \n $methodBody \n"
   }	
}
