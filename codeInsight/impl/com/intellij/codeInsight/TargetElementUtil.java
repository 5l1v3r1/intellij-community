package com.intellij.codeInsight;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class TargetElementUtil extends TargetElementUtilBase {
  public static final int NEW_AS_CONSTRUCTOR = 0x04;
  public static final int THIS_ACCEPTED = 0x10;
  public static final int SUPER_ACCEPTED = 0x20;

  @Override
  public int getAllAccepted() {
    return super.getAllAccepted() | NEW_AS_CONSTRUCTOR | THIS_ACCEPTED | SUPER_ACCEPTED;
  }

  @Nullable
  @Override
  public PsiElement findTargetElement(final Editor editor, final int flags, final int offset) {
    final PsiElement element = super.findTargetElement(editor, flags, offset);
    if (element instanceof PsiKeyword) {
      if (element.getParent() instanceof PsiThisExpression) {
        if ((flags & THIS_ACCEPTED) == 0) return null;
        PsiType type = ((PsiThisExpression)element.getParent()).getType();
        if (!(type instanceof PsiClassType)) return null;
        return ((PsiClassType)type).resolve();
      }

      if (element.getParent() instanceof PsiSuperExpression) {
        if ((flags & SUPER_ACCEPTED) == 0) return null;
        PsiType type = ((PsiSuperExpression)element.getParent()).getType();
        if (!(type instanceof PsiClassType)) return null;
        return ((PsiClassType)type).resolve();
      }
    }
    return element;
  }

  protected boolean isAcceptableReferencedElement(final PsiElement element, final PsiElement referenceOrReferencedElement) {
    return super.isAcceptableReferencedElement(element, referenceOrReferencedElement) &&
           !isEnumConstantReference(element, referenceOrReferencedElement);
  }

  private static boolean isEnumConstantReference(final PsiElement element, final PsiElement referenceOrReferencedElement) {
    return element != null &&
           element.getParent() instanceof PsiEnumConstant &&
           referenceOrReferencedElement instanceof PsiMethod &&
           ((PsiMethod)referenceOrReferencedElement).isConstructor();
  }

  @Nullable
  protected PsiElement getReferenceOrReferencedElement(PsiFile file, Editor editor, int flags, int offset) {
    PsiReference ref = TargetElementUtilBase.findReference(editor, offset);
    if (ref == null) return null;
    PsiManager manager = file.getManager();

    final PsiElement referenceElement = ref.getElement();
    PsiElement refElement;
    if (ref instanceof PsiJavaReference) {
      refElement = ((PsiJavaReference)ref).advancedResolve(true).getElement();
    }
    else {
      refElement = ref.resolve();
    }

    if (refElement == null) {
      DaemonCodeAnalyzer.getInstance(manager.getProject()).updateVisibleHighlighters(editor);
      return null;
    }
    else {
      if ((flags & NEW_AS_CONSTRUCTOR) != 0) {
        PsiElement parent = referenceElement.getParent();
        if (parent instanceof PsiAnonymousClass) {
          parent = parent.getParent();
        }
        if (parent instanceof PsiNewExpression) {
          PsiMethod constructor = ((PsiNewExpression)parent).resolveConstructor();
          if (constructor != null) {
            refElement = constructor;
          }
        }
      }
      if (refElement instanceof PsiClass && refElement.getContainingFile().getVirtualFile() == null) { // in mirror file of compiled class
        String qualifiedName = ((PsiClass)refElement).getQualifiedName();
        if (qualifiedName == null) return null;
        return JavaPsiFacade.getInstance(manager.getProject()).findClass(qualifiedName, refElement.getResolveScope());
      }
      return refElement;
    }
  }


  protected PsiElement getNamedElement(final PsiElement element) {
    PsiElement parent = element.getParent();
    if (element instanceof PsiIdentifier) {
      if (parent instanceof PsiClass && element.equals(((PsiClass)parent).getNameIdentifier())) {
        return parent;
      }
      else if (parent instanceof PsiVariable && element.equals(((PsiVariable)parent).getNameIdentifier())) {
        return parent;
      }
      else if (parent instanceof PsiMethod && element.equals(((PsiMethod)parent).getNameIdentifier())) {
        return parent;
      }
      else if (parent instanceof PsiLabeledStatement && element.equals(((PsiLabeledStatement)parent).getLabelIdentifier())) {
        return parent;
      }
    }
    else if ((parent = PsiTreeUtil.getParentOfType(element, PsiNamedElement.class, false)) != null) {
      // A bit hacky depends on navigation offset correctly overridden
      if (parent.getTextOffset() == element.getTextRange().getStartOffset() && !(parent instanceof XmlAttribute)) {
        return parent;
      }
    }
    return null;
  }

  @Nullable
  public static PsiReferenceExpression findReferenceExpression(Editor editor) {
    final PsiReference ref = findReference(editor);
    return ref instanceof PsiReferenceExpression ? (PsiReferenceExpression)ref : null;
  }

  @Override
  public PsiElement adjustReference(@NotNull final PsiReference ref) {
    final PsiElement parent = ref.getElement().getParent();
    if (parent instanceof PsiMethodCallExpression) return parent;
    return super.adjustReference(ref);
  }

  @Nullable
  @Override
  public PsiElement adjustElement(final Editor editor, final int flags, final PsiElement element, final PsiElement contextElement) {
    if (element != null) {
      if (element instanceof PsiAnonymousClass) {
        return ((PsiAnonymousClass)element).getBaseClassType().resolve();
      }
      return element;
    }
    if (contextElement == null) return null;
    final PsiElement parent = contextElement.getParent();
    if (parent instanceof XmlText || parent instanceof XmlAttributeValue) {
      return TargetElementUtilBase.getInstance().findTargetElement(editor, flags, parent.getParent().getTextRange().getStartOffset() + 1);
    }
    else if (parent instanceof XmlTag || parent instanceof XmlAttribute) {
      return TargetElementUtilBase.getInstance().findTargetElement(editor, flags, parent.getTextRange().getStartOffset() + 1);
    }
    return null;
  }

  public Collection<PsiElement> getTargetCandidates(final PsiReference reference) {
    PsiElement parent = reference.getElement().getParent();
    if (parent instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression callExpr = (PsiMethodCallExpression) parent;
      boolean allowStatics = false;
      PsiExpression qualifier = callExpr.getMethodExpression().getQualifierExpression();
      if (qualifier == null) {
        allowStatics = true;
      } else if (qualifier instanceof PsiJavaCodeReferenceElement) {
        PsiElement referee = ((PsiJavaCodeReferenceElement) qualifier).advancedResolve(true).getElement();
        if (referee instanceof PsiClass) allowStatics = true;
      }
      PsiResolveHelper helper = JavaPsiFacade.getInstance(parent.getProject()).getResolveHelper();
      PsiElement[] candidates = PsiUtil.mapElements(helper.getReferencedMethodCandidates(callExpr, false));
      ArrayList<PsiElement> methods = new ArrayList<PsiElement>();
      for (PsiElement candidate1 : candidates) {
        PsiMethod candidate = (PsiMethod)candidate1;
        if (candidate.hasModifierProperty(PsiModifier.STATIC) && !allowStatics) continue;
        List<PsiMethod> supers = Arrays.asList(candidate.findSuperMethods());
        if (supers.isEmpty()) {
          methods.add(candidate);
        }
        else {
          methods.addAll(supers);
        }
      }
      return methods;
    }

    return super.getTargetCandidates(reference);
  }
}
