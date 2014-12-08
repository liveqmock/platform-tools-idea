/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.impl.light.LightTypeElement;
import com.intellij.psi.impl.source.tree.PsiWhiteSpaceImpl;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.text.UniqueNameGenerator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class GenerateMembersUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.generation.GenerateMembersUtil");

  private GenerateMembersUtil() {
  }

  @NotNull
  public static <T extends GenerationInfo> List<T> insertMembersAtOffset(PsiFile file, int offset, @NotNull List<T> memberPrototypes) throws IncorrectOperationException {
    if (memberPrototypes.isEmpty()) return memberPrototypes;
    final PsiElement leaf = file.findElementAt(offset);
    if (leaf == null) return Collections.emptyList();

    PsiClass aClass = findClassAtOffset(file, leaf);
    if (aClass == null) return Collections.emptyList();
    PsiElement anchor = memberPrototypes.get(0).findInsertionAnchor(aClass, leaf);

    if (anchor instanceof PsiWhiteSpace) {
      final ASTNode spaceNode = anchor.getNode();
      anchor = anchor.getNextSibling();

      assert spaceNode != null;
      if (spaceNode.getStartOffset() <= offset && spaceNode.getStartOffset() + spaceNode.getTextLength() >= offset) {
        String whiteSpace = spaceNode.getText().substring(0, offset - spaceNode.getStartOffset());
        if (!StringUtil.containsLineBreak(whiteSpace)) {
          // There is a possible case that the caret is located at the end of the line that already contains expression, say, we
          // want to override particular method while caret is located after the field.
          // Example - consider that we want to override toString() method at the class below:
          //     class Test {
          //         int i;<caret>
          //     }
          // We want to add line feed then in order to avoid situation like below:
          //     class Test {
          //         int i;@Override String toString() {
          //             super.toString();
          //         }
          //     }
          whiteSpace += "\n";
        }
        final PsiParserFacade parserFacade = PsiParserFacade.SERVICE.getInstance(file.getProject());
        final ASTNode singleNewLineWhitespace = parserFacade.createWhiteSpaceFromText(whiteSpace).getNode();
        if (singleNewLineWhitespace != null) {
          spaceNode.getTreeParent().replaceChild(spaceNode, singleNewLineWhitespace); // See http://jetbrains.net/jira/browse/IDEADEV-12837
        }
      }
    }

    // Q: shouldn't it be somewhere in PSI?
    PsiElement element = anchor;
    while (true) {
      if (element == null) break;
      if (element instanceof PsiField || element instanceof PsiMethod || element instanceof PsiClassInitializer) break;
      element = element.getNextSibling();
    }
    if (element instanceof PsiField) {
      PsiField field = (PsiField)element;
      PsiTypeElement typeElement = field.getTypeElement();
      if (typeElement != null && !field.equals(typeElement.getParent())) {
        field.normalizeDeclaration();
        anchor = field;
      }
    }

    return insertMembersBeforeAnchor(aClass, anchor, memberPrototypes);
  }

  @NotNull
  public static <T extends GenerationInfo> List<T> insertMembersBeforeAnchor(PsiClass aClass, @Nullable PsiElement anchor, @NotNull List<T> memberPrototypes) throws IncorrectOperationException {
    boolean before = true;
    for (T memberPrototype : memberPrototypes) {
      memberPrototype.insert(aClass, anchor, before);
      before = false;
      anchor = memberPrototype.getPsiMember();
    }
    return memberPrototypes;
  }

  /**
   * @see GenerationInfo#positionCaret(com.intellij.openapi.editor.Editor, boolean)
   */
  public static void positionCaret(@NotNull Editor editor, @NotNull PsiElement firstMember, boolean toEditMethodBody) {
    LOG.assertTrue(firstMember.isValid());
    Project project = firstMember.getProject();

    if (toEditMethodBody) {
      PsiMethod method = (PsiMethod)firstMember;
      PsiCodeBlock body = method.getBody();
      if (body != null) {
        PsiElement firstBodyElement = body.getFirstBodyElement();
        PsiElement l = firstBodyElement;
        while (l instanceof PsiWhiteSpace) l = l.getNextSibling();
        if (l == null) l = body;
        PsiElement lastBodyElement = body.getLastBodyElement();
        PsiElement r = lastBodyElement;
        while (r instanceof PsiWhiteSpace) r = r.getPrevSibling();
        if (r == null) r = body;

        int start = l.getTextRange().getStartOffset();
        int end = r.getTextRange().getEndOffset();

        boolean adjustLineIndent = false;

        // body is whitespace
        if (start > end &&
            firstBodyElement == lastBodyElement &&
            firstBodyElement instanceof PsiWhiteSpaceImpl
          ) {
          CharSequence chars = ((PsiWhiteSpaceImpl)firstBodyElement).getChars();
          if (chars.length() > 1 && chars.charAt(0) == '\n' && chars.charAt(1) == '\n') {
            start = end = firstBodyElement.getTextRange().getStartOffset() + 1;
            adjustLineIndent = true;
          }
        }

        editor.getCaretModel().moveToOffset(Math.min(start, end));
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        if (start < end) {
          //Not an empty body
          editor.getSelectionModel().setSelection(start, end);
        } else if (adjustLineIndent) {
          Document document = editor.getDocument();
          RangeMarker marker = document.createRangeMarker(start, start);
          PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
          if (marker.isValid()) {
            CodeStyleManager.getInstance(project).adjustLineIndent(document, marker.getStartOffset());
          }
        }
        return;
      }
    }

    int offset;
    if (firstMember instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)firstMember;
      PsiCodeBlock body = method.getBody();
      if (body == null) {
        offset = method.getTextRange().getStartOffset();
      }
      else {
        offset = body.getLBrace().getTextRange().getEndOffset();
      }
    }
    else {
      offset = firstMember.getTextRange().getStartOffset();
    }

    editor.getCaretModel().moveToOffset(offset);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    editor.getSelectionModel().removeSelection();
  }

  public static PsiElement insert(@NotNull PsiClass aClass, @NotNull PsiMember member, @Nullable PsiElement anchor, boolean before) throws IncorrectOperationException {
    if (member instanceof PsiMethod) {
      if (!aClass.isInterface()) {
        final PsiParameter[] parameters = ((PsiMethod)member).getParameterList().getParameters();
        final boolean generateFinals = CodeStyleSettingsManager.getSettings(aClass.getProject()).GENERATE_FINAL_PARAMETERS;
        for (final PsiParameter parameter : parameters) {
          final PsiModifierList modifierList = parameter.getModifierList();
          assert modifierList != null;
          modifierList.setModifierProperty(PsiModifier.FINAL, generateFinals);
        }
      }
    }

    if (anchor != null) {
      return before ? aClass.addBefore(member, anchor) : aClass.addAfter(member, anchor);
    }
    else {
      return aClass.add(member);
    }
  }

  @Nullable
  private static PsiClass findClassAtOffset(PsiFile file, PsiElement leaf) {
    PsiElement element = leaf;
    while (element != null && !(element instanceof PsiFile)) {
      if (element instanceof PsiClass && !(element instanceof PsiTypeParameter)) {
        final PsiClass psiClass = (PsiClass)element;
        if (psiClass.isEnum()) {
          PsiElement lastChild = null;
          for (PsiElement child : psiClass.getChildren()) {
            if (child instanceof PsiJavaToken && ";".equals(child.getText())) {
              lastChild = child;
              break;
            }
            else if (child instanceof PsiJavaToken && ",".equals(child.getText()) || child instanceof PsiEnumConstant) {
              lastChild = child;
            }
          }
          if (lastChild != null) {
            int adjustedOffset = lastChild.getTextRange().getEndOffset();
            if (leaf.getTextRange().getEndOffset() <= adjustedOffset) return findClassAtOffset(file, file.findElementAt(adjustedOffset));
          }
        }
        return psiClass;
      }
      element = element.getParent();
    }
    return null;
  }

  public static PsiMethod substituteGenericMethod(PsiMethod method, final PsiSubstitutor substitutor) {
    return substituteGenericMethod(method, substitutor, null);
  }

  public static PsiMethod substituteGenericMethod(@NotNull PsiMethod sourceMethod,
                                                  @NotNull PsiSubstitutor substitutor,
                                                  @Nullable PsiElement target) {
    final Project project = sourceMethod.getProject();
    final JVMElementFactory factory = getFactory(sourceMethod, target);
    final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);

    try {
      final PsiMethod resultMethod = createMethod(factory, sourceMethod, target);
      copyDocComment(sourceMethod, resultMethod, factory);
      copyModifiers(sourceMethod.getModifierList(), resultMethod.getModifierList());
      final PsiSubstitutor collisionResolvedSubstitutor =
        substituteTypeParameters(factory, target, sourceMethod.getTypeParameterList(), resultMethod.getTypeParameterList(), substitutor, sourceMethod);
      substituteReturnType(PsiManager.getInstance(project), resultMethod, sourceMethod.getReturnType(), collisionResolvedSubstitutor);
      substituteParameters(factory, codeStyleManager, sourceMethod.getParameterList(), resultMethod.getParameterList(), collisionResolvedSubstitutor, target);
      final List<PsiClassType> thrownTypes = ExceptionUtil.collectSubstituted(collisionResolvedSubstitutor, sourceMethod.getThrowsList().getReferencedTypes());
      if (target instanceof PsiClass) {
        final PsiClass[] supers = ((PsiClass)target).getSupers();
        for (PsiClass aSuper : supers) {
          final PsiMethod psiMethod = aSuper.findMethodBySignature(sourceMethod, true);
          if (psiMethod != null && psiMethod != sourceMethod) {
            ExceptionUtil.retainExceptions(thrownTypes, ExceptionUtil.collectSubstituted(TypeConversionUtil.getSuperClassSubstitutor(aSuper, (PsiClass)target, PsiSubstitutor.EMPTY), psiMethod.getThrowsList().getReferencedTypes()));
          }
        }
      }
      substituteThrows(factory, resultMethod.getThrowsList(), collisionResolvedSubstitutor, sourceMethod, thrownTypes);
      return resultMethod;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return sourceMethod;
    }
  }

  private static void copyModifiers(@NotNull PsiModifierList sourceModifierList,
                                    @NotNull PsiModifierList targetModifierList) {
    VisibilityUtil.setVisibility(targetModifierList, VisibilityUtil.getVisibilityModifier(sourceModifierList));
  }

  @NotNull
  private static PsiSubstitutor substituteTypeParameters(@NotNull JVMElementFactory factory,
                                                         @Nullable PsiElement target,
                                                         @Nullable PsiTypeParameterList sourceTypeParameterList,
                                                         @Nullable PsiTypeParameterList targetTypeParameterList,
                                                         @NotNull PsiSubstitutor substitutor, 
                                                         @NotNull PsiMethod sourceMethod) {
    if (sourceTypeParameterList == null || targetTypeParameterList == null) {
      return substitutor;
    }

    final Map<PsiTypeParameter, PsiType> substitutionMap = new HashMap<PsiTypeParameter, PsiType>(substitutor.getSubstitutionMap());
    for (PsiTypeParameter typeParam : sourceTypeParameterList.getTypeParameters()) {
      final PsiTypeParameter substitutedTypeParam = substituteTypeParameter(factory, typeParam, substitutor, sourceMethod);

      final PsiTypeParameter resolvedTypeParam = resolveTypeParametersCollision(factory, sourceTypeParameterList, target,
                                                                                substitutedTypeParam, substitutor);
      targetTypeParameterList.add(resolvedTypeParam);
      if (substitutedTypeParam != resolvedTypeParam) {
        substitutionMap.put(typeParam, factory.createType(resolvedTypeParam));
      }
    }
    return substitutionMap.isEmpty() ? substitutor : factory.createSubstitutor(substitutionMap);
  }

  @NotNull
  private static PsiTypeParameter resolveTypeParametersCollision(@NotNull JVMElementFactory factory,
                                                                 @NotNull PsiTypeParameterList sourceTypeParameterList,
                                                                 @Nullable PsiElement target,
                                                                 @NotNull PsiTypeParameter typeParam,
                                                                 @NotNull PsiSubstitutor substitutor) {
    for (PsiType type : substitutor.getSubstitutionMap().values()) {
      if (type != null && Comparing.equal(type.getCanonicalText(), typeParam.getName())) {
        final String newName = suggestUniqueTypeParameterName(typeParam.getName(), sourceTypeParameterList, PsiTreeUtil.getParentOfType(target, PsiClass.class, false));
        final PsiTypeParameter newTypeParameter = factory.createTypeParameter(newName, typeParam.getSuperTypes());
        substitutor.put(typeParam, factory.createType(newTypeParameter));
        return newTypeParameter;
      }
    }
    return factory.createTypeParameter(typeParam.getName(), typeParam.getSuperTypes());
  }

  @NotNull
  private static String suggestUniqueTypeParameterName(@NonNls String baseName, @NotNull PsiTypeParameterList typeParameterList, @Nullable PsiClass targetClass) {
    int i = 0;
    while (true) {
      final String newName = baseName + ++i;
      if (checkUniqueTypeParameterName(newName, typeParameterList) && (targetClass == null || checkUniqueTypeParameterName(newName, targetClass.getTypeParameterList()))) {
        return newName;
      }
    }
  }


  private static boolean checkUniqueTypeParameterName(@NonNls @NotNull String baseName, @Nullable PsiTypeParameterList typeParameterList) {
    if (typeParameterList == null) return true;

    for (PsiTypeParameter typeParameter : typeParameterList.getTypeParameters()) {
      if (Comparing.equal(typeParameter.getName(), baseName)) {
        return false;
      }
    }
    return true;
  }


  @NotNull
  private static PsiTypeParameter substituteTypeParameter(final @NotNull JVMElementFactory factory,
                                                          @NotNull PsiTypeParameter typeParameter,
                                                          final @NotNull PsiSubstitutor substitutor, 
                                                          @NotNull final PsiMethod sourceMethod) {
    final PsiElement copy = typeParameter.copy();
    final Map<PsiElement, PsiElement> replacementMap = new HashMap<PsiElement, PsiElement>();
    copy.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        super.visitReferenceElement(reference);
        final PsiElement resolve = reference.resolve();
        if (resolve instanceof PsiTypeParameter) {
          final PsiType type = factory.createType((PsiTypeParameter)resolve);
          replacementMap.put(reference, factory.createReferenceElementByType((PsiClassType)substituteType(substitutor, type, sourceMethod)));
        }
      }
    });
    return (PsiTypeParameter)RefactoringUtil.replaceElementsWithMap(copy, replacementMap);
  }

  private static void substituteParameters(@NotNull JVMElementFactory factory,
                                           @NotNull JavaCodeStyleManager codeStyleManager,
                                           @NotNull PsiParameterList sourceParameterList,
                                           @NotNull PsiParameterList targetParameterList,
                                           @NotNull PsiSubstitutor substitutor, PsiElement target) {
    PsiParameter[] parameters = sourceParameterList.getParameters();
    UniqueNameGenerator generator = new UniqueNameGenerator();

    for (PsiParameter parameter : parameters) {
      final PsiType parameterType = parameter.getType();
      final PsiType substituted = substituteType(substitutor, parameterType, (PsiMethod)parameter.getDeclarationScope());
      @NonNls String paramName = parameter.getName();
      boolean isBaseNameGenerated = true;
      final boolean isSubstituted = substituted.equals(parameterType);
      if (!isSubstituted && isBaseNameGenerated(codeStyleManager, TypeConversionUtil.erasure(parameterType), paramName)) {
        isBaseNameGenerated = false;
      }

      if (paramName == null ||
          isBaseNameGenerated && !isSubstituted && isBaseNameGenerated(codeStyleManager, parameterType, paramName) ||
          !factory.isValidParameterName(paramName)) {
        String[] names = codeStyleManager.suggestVariableName(VariableKind.PARAMETER, null, null, substituted).names;
        if (names.length > 0) {
          paramName = generator.generateUniqueName(names[0]);
        }
        else {
          paramName = generator.generateUniqueName("p");
        }
      }
      else if (!generator.value(paramName)) {
        paramName = generator.generateUniqueName(paramName);
      }
      generator.addExistingName(paramName);
      final PsiParameter newParameter = factory.createParameter(paramName, substituted, target);
      copyOrReplaceModifierList(parameter, newParameter);
      targetParameterList.add(newParameter);
    }
  }

  private static void substituteThrows(@NotNull JVMElementFactory factory,
                                       @NotNull PsiReferenceList targetThrowsList,
                                       @NotNull PsiSubstitutor substitutor,
                                       @NotNull PsiMethod sourceMethod, 
                                       List<PsiClassType> thrownTypes) {
    for (PsiClassType thrownType : thrownTypes) {
      targetThrowsList.add(factory.createReferenceElementByType((PsiClassType)substituteType(substitutor, thrownType, sourceMethod)));
    }
  }

  private static void copyDocComment(PsiMethod source, PsiMethod target, JVMElementFactory factory) {
    final PsiElement navigationElement = source.getNavigationElement();
    if (navigationElement instanceof PsiDocCommentOwner) {
      final PsiDocComment docComment = ((PsiDocCommentOwner)navigationElement).getDocComment();
      if (docComment != null) {
        target.addAfter(factory.createDocCommentFromText(docComment.getText()), null);
      }
    }
  }

  @NotNull
  private static PsiMethod createMethod(@NotNull JVMElementFactory factory,
                                        @NotNull PsiMethod method, PsiElement target) {
    if (method.isConstructor()) {
      return factory.createConstructor(method.getName(), target);
    }
    return factory.createMethod(method.getName(), PsiType.VOID);
  }

  private static void substituteReturnType(@NotNull PsiManager manager,
                                           @NotNull PsiMethod method,
                                           @Nullable PsiType returnType,
                                           @NotNull PsiSubstitutor substitutor) {
    final PsiTypeElement returnTypeElement = method.getReturnTypeElement();
    if (returnTypeElement == null || returnType == null) {
      return;
    }
    final PsiType substitutedReturnType = substituteType(substitutor, returnType, method);

    returnTypeElement.replace(new LightTypeElement(manager, substitutedReturnType instanceof PsiWildcardType ? TypeConversionUtil.erasure(substitutedReturnType) : substitutedReturnType));
  }

  @NotNull
  private static JVMElementFactory getFactory(@NotNull PsiMethod method, @Nullable PsiElement target) {
    if (target == null) {
      return JavaPsiFacade.getInstance(method.getProject()).getElementFactory();
    }

    return JVMElementFactories.getFactory(target.getLanguage(), method.getProject());
  }

  private static boolean isBaseNameGenerated(JavaCodeStyleManager csManager, PsiType parameterType, String paramName) {
    return Arrays.asList(csManager.suggestVariableName(VariableKind.PARAMETER, null, null, parameterType).names).contains(paramName);
  }

  private static PsiType substituteType(final PsiSubstitutor substitutor, final PsiType type, @NotNull PsiTypeParameterListOwner owner) {
    if (PsiUtil.isRawSubstitutor(owner, substitutor)) {
      return TypeConversionUtil.erasure(type);
    }
    final PsiType psiType = substitutor.substitute(type);
    if (psiType != null) {
      final PsiType deepComponentType = psiType.getDeepComponentType();
      if (!(deepComponentType instanceof PsiCapturedWildcardType || deepComponentType instanceof PsiWildcardType)){
        return psiType;
      }
    }
    return TypeConversionUtil.erasure(type);
  }

  public static boolean isChildInRange(PsiElement child, PsiElement first, PsiElement last) {
    if (child.equals(first)) return true;
    while (true) {
      if (child.equals(first)) return false; // before first
      if (child.equals(last)) return true;
      child = child.getNextSibling();
      if (child == null) return false;
    }
  }

  public static void setupGeneratedMethod(PsiMethod method) {
    PsiClass base = method.getContainingClass().getSuperClass();
    PsiMethod overridden = base == null ? null : base.findMethodBySignature(method, true);

    if (overridden == null) {
      CreateFromUsageUtils.setupMethodBody(method, method.getContainingClass());
      return;
    }

    OverrideImplementUtil.setupMethodBody(method, overridden, method.getContainingClass());
    OverrideImplementUtil.annotateOnOverrideImplement(method, base, overridden);
  }

  public static void copyOrReplaceModifierList(@NotNull PsiModifierListOwner sourceParam, @NotNull PsiModifierListOwner targetParam) {
    PsiModifierList sourceModifierList = sourceParam.getModifierList();
    PsiModifierList targetModifierList = targetParam.getModifierList();
    if (sourceModifierList != null && targetModifierList != null) {
      if (sourceParam.getLanguage() == targetParam.getLanguage()) {
        targetModifierList = (PsiModifierList)targetModifierList.replace(sourceModifierList);
      }
      else {
        JVMElementFactory factory = JVMElementFactories.requireFactory(targetParam.getLanguage(), targetParam.getProject());
        for (PsiAnnotation annotation : sourceModifierList.getAnnotations()) {
          targetModifierList.add(factory.createAnnotationFromText(annotation.getText(), sourceParam));
        }
        for (@PsiModifier.ModifierConstant String m : PsiModifier.MODIFIERS) {
          targetModifierList.setModifierProperty(m, sourceParam.hasModifierProperty(m));
        }
      }
      processAnnotations(sourceModifierList.getProject(), targetModifierList, targetModifierList.getResolveScope());
    }
  }

  private static void processAnnotations(Project project, PsiModifierList modifierList, GlobalSearchScope moduleScope) {
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final Set<String> toRemove = new HashSet<String>();
    for (PsiAnnotation annotation : modifierList.getAnnotations()) {
      final String qualifiedName = annotation.getQualifiedName();
      if (qualifiedName != null) {
        for (OverrideImplementsAnnotationsHandler handler : Extensions.getExtensions(OverrideImplementsAnnotationsHandler.EP_NAME)) {
          final String[] annotations2Remove = handler.annotationsToRemove(project, qualifiedName);
          Collections.addAll(toRemove, annotations2Remove);
          if (moduleScope != null && psiFacade.findClass(qualifiedName, moduleScope) == null) {
            toRemove.add(qualifiedName);
          }
        }
      }
    }
    for (String fqn : toRemove) {
      final PsiAnnotation psiAnnotation = modifierList.findAnnotation(fqn);
      if (psiAnnotation != null) {
        psiAnnotation.delete();
      }
    }
  }

  @Nullable
  public static PsiMethod generateGetterPrototype(@NotNull PsiField field) {
    return annotateOnOverrideImplement(field.getContainingClass(), PropertyUtil.generateGetterPrototype(field));
  }

  @Nullable
  public static PsiMethod generateSetterPrototype(@NotNull PsiField field) {
    return annotateOnOverrideImplement(field.getContainingClass(), PropertyUtil.generateSetterPrototype(field));
  }

  @Nullable
  private static PsiMethod annotateOnOverrideImplement(@Nullable PsiClass targetClass, @Nullable PsiMethod generated) {
    if (generated == null || targetClass == null) return generated;

    if (CodeStyleSettingsManager.getSettings(targetClass.getProject()).INSERT_OVERRIDE_ANNOTATION) {
      PsiMethod superMethod = targetClass.findMethodBySignature(generated, true);
      if (superMethod != null && superMethod.getContainingClass() != targetClass) {
        OverrideImplementUtil.annotateOnOverrideImplement(generated, targetClass, superMethod, true);
      }
    }
    return generated;
  }
}