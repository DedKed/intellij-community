/*
 * Copyright 2007 Sascha Weinreuter
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

package org.intellij.plugins.xsltDebugger.rt.engine.remote;

import java.io.File;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface RemoteBreakpointManager extends Remote {
  RemoteBreakpoint setBreakpoint(File file, int line) throws RemoteException;

  RemoteBreakpoint setBreakpoint(String uri, int line) throws RemoteException;

  void removeBreakpoint(String uri, int line) throws RemoteException;

  List<RemoteBreakpoint> getBreakpoints() throws RemoteException;

  RemoteBreakpoint getBreakpoint(String uri, int lineNumber) throws RemoteException;
}
