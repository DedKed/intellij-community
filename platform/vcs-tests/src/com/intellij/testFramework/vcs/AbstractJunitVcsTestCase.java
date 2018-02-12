/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.testFramework.vcs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.testFramework.TestLoggerFactory;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.runner.Description;

public abstract class AbstractJunitVcsTestCase extends AbstractVcsTestCase {
  static {
    Logger.setFactory(TestLoggerFactory.class);
  }

  @Rule
  public TestName name= new TestName(){
    @Override
    protected void succeeded(Description description) {
      TestLoggerFactory.onTestFinished(true);
    }

    @Override
    protected void failed(Throwable e, Description description) {
      TestLoggerFactory.onTestFinished(false);
    }
  };

  protected String getTestName() {
    return name.getMethodName();
  }

}
