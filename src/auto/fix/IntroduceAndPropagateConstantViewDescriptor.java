/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package auto.fix;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;

/**
 * User: Call me Ismail
 * Date: May 3, 2010
 * Time: 8:59:21 PM
 */
public class IntroduceAndPropagateConstantViewDescriptor implements UsageViewDescriptor
{
    private HashSet<PsiExpression> myOccurrences;

    public IntroduceAndPropagateConstantViewDescriptor(HashSet<PsiExpression> occurrences)
    {
        myOccurrences = occurrences;
    }

    @NotNull
    public PsiElement[] getElements()
    {
        PsiElement[] retVal = new PsiElement[myOccurrences.size()];
        int i = 0;
        for(PsiExpression expression : myOccurrences)
        {
            retVal[i] = expression.getOriginalElement();
            i++;
        }
        return retVal;
    }

    public String getProcessedElementsHeader()
    {
        return StringUtil.capitalize(UsageViewUtil.getType(myOccurrences.iterator().next()));
    }

    public String getCodeReferencesText(int usagesCount, int filesCount)
    {
        return "Extracted literal expressions to be replaced";
    }

    public String getCommentReferencesText(int usagesCount, int filesCount)
    {
        return "Comments referencing literal expressions to be replaced";
    }
}
