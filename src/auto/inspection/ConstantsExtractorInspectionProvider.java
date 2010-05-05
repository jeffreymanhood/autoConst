package auto.inspection;/*
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
import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.openapi.components.ApplicationComponent;
import org.jetbrains.annotations.NotNull;

/**
 * User: Call me Ismail
 * Date: Apr 21, 2010
 * Time: 8:25:00 PM
 */
public class ConstantsExtractorInspectionProvider implements ApplicationComponent, InspectionToolProvider
{
    public ConstantsExtractorInspectionProvider()
    {
    }

    public void initComponent()
    {
        // TODO: insert component initialization logic here
    }

    public void disposeComponent()
    {
        // TODO: insert component disposal logic here
    }

    @NotNull
    public String getComponentName()
    {
        return "auto.inspection.ConstantsExtractorInspectionProvider";
    }

    public Class[] getInspectionClasses()
    {
        return new Class[]{ConstantsExtractionInspection.class};
    }
}
