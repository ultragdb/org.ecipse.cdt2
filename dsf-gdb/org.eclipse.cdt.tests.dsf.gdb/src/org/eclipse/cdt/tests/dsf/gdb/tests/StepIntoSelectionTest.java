/*******************************************************************************
 * Copyright (c) 2013 Ericsson and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Alvaro Sanchez-Leon (Ericsson AB) - Support for Step into selection (bug 244865)
 *******************************************************************************/
package org.eclipse.cdt.tests.dsf.gdb.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeUnit;

import org.eclipse.cdt.core.model.IFunctionDeclaration;
import org.eclipse.cdt.debug.core.ICDTLaunchConfigurationConstants;
import org.eclipse.cdt.dsf.concurrent.DataRequestMonitor;
import org.eclipse.cdt.dsf.concurrent.Query;
import org.eclipse.cdt.dsf.debug.service.IRunControl.IContainerDMContext;
import org.eclipse.cdt.dsf.debug.service.IRunControl.IExecutionDMContext;
import org.eclipse.cdt.dsf.debug.service.IRunControl.ISuspendedDMEvent;
import org.eclipse.cdt.dsf.debug.service.IRunControl.StepType;
import org.eclipse.cdt.dsf.debug.service.IRunControl3;
import org.eclipse.cdt.dsf.mi.service.command.events.IMIDMEvent;
import org.eclipse.cdt.dsf.mi.service.command.events.MIStoppedEvent;
import org.eclipse.cdt.dsf.mi.service.command.output.MIFrame;
import org.eclipse.cdt.dsf.service.DsfServicesTracker;
import org.eclipse.cdt.dsf.service.DsfSession;
import org.eclipse.cdt.internal.core.model.FunctionDeclaration;
import org.eclipse.cdt.tests.dsf.gdb.framework.BackgroundRunner;
import org.eclipse.cdt.tests.dsf.gdb.framework.BaseTestCase;
import org.eclipse.cdt.tests.dsf.gdb.framework.ServiceEventWaitor;
import org.eclipse.cdt.tests.dsf.gdb.framework.SyncUtil;
import org.eclipse.cdt.tests.dsf.gdb.launching.TestsPlugin;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests Non Stop GDB RunControl "Step into Selection feature"
 * 
 */
@SuppressWarnings("restriction")
@RunWith(BackgroundRunner.class)
public class StepIntoSelectionTest extends BaseTestCase {

	private DsfServicesTracker fServicesTracker;
	private DsfSession fSession;

	private IRunControl3 fRunCtrl;

	private static final String SRC_FILE = "StepIntoSelectionTestApp.cc";
	private static final String HDR_FILE = "StepIntoSelection.h";
	private static final int FOO_LINE = 11;
	private static final int BAR_LINE = 20;
	private static final int VALUE_LINE = 5;
	private static final int ADD_WITH_ARG_LINE = 30;
	private static final int ADD_NO_ARG_LINE = 35;
	
	//Target Functions
	private final static FunctionDeclaration funcFoo = new FunctionDeclaration(null, "foo");
	private final static FunctionDeclaration funcBar = new FunctionDeclaration(null, "bar");
	private final static FunctionDeclaration funcRecursive = new FunctionDeclaration(null, "recursiveTest");
	private final static FunctionDeclaration funcValue = new FunctionDeclaration(null, "value");
	private final static FunctionDeclaration funcAddNoArg = new FunctionDeclaration(null, "add");
	private final static FunctionDeclaration funcAddWithArg = new FunctionDeclaration(null, "add");

	static {
		funcBar.setParameterTypes(new String[]{"int"});
		funcRecursive.setParameterTypes(new String[]{"int"});
		funcAddWithArg.setParameterTypes(new String[]{"int"});
	}

	class ResultContext {
		MIStoppedEvent fEvent = null;
		IExecutionDMContext fContext = null;
	
		public ResultContext(MIStoppedEvent event, IExecutionDMContext context) {
			this.fEvent = event;
			this.fContext = context;
		}
		
		public MIStoppedEvent getEvent() {
			return fEvent;
		}
	
		public IExecutionDMContext getContext() {
			return fContext;
		}
	}

	@Override
	public void doBeforeTest() throws Exception {
		super.doBeforeTest();

		Runnable runnable = new Runnable() {
			@Override
			public void run() {
				fServicesTracker = new DsfServicesTracker(TestsPlugin.getBundleContext(), fSession.getId());
				fRunCtrl = fServicesTracker.getService(IRunControl3.class);
			}
		};
		fSession = getGDBLaunch().getSession();
		fSession.getExecutor().submit(runnable).get();
	}

	@Override
	public void doAfterTest() throws Exception {
		super.doAfterTest();

		fServicesTracker.dispose();
	}

	@Override
	protected void setLaunchAttributes() {
		super.setLaunchAttributes();
		setLaunchAttribute(ICDTLaunchConfigurationConstants.ATTR_PROGRAM_NAME, "data/launch/bin/StepIntoSelectionTestApp.exe");
	}

	private void validateLocation(ISuspendedDMEvent suspendedEvent, String expectedFunction, 
			                      String expectedFile, int expectedLine, int expectedDepth) throws Throwable {
		assertNotNull(suspendedEvent);

		assertTrue("Expected suspended event to be IMIDMEvent, but it was not.", suspendedEvent instanceof IMIDMEvent);
		Object miEvent = ((IMIDMEvent)suspendedEvent).getMIEvent();
		
		assertTrue("Expected mi event to be MIStoppedEvent, but it was not.", miEvent instanceof MIStoppedEvent);
		MIStoppedEvent stoppedEvent = (MIStoppedEvent)miEvent;
		
		// Validate that the last stopped frame received is at the specified location
		MIFrame frame = stoppedEvent.getFrame();
		assertTrue("Not inside the expected function.  Expected " +
				   expectedFunction + " but got " +
				   frame.getFunction(), 
				   frame.getFunction().endsWith(expectedFunction));
		assertEquals(expectedLine, frame.getLine());
		
		assertTrue("Not inside the expected file.  Expected " +
				   expectedFile + " but got " + frame.getFile(), 
				   frame.getFile().endsWith(expectedFile));

		int newDepth = SyncUtil.getStackDepth(stoppedEvent.getDMContext());
		assertEquals(expectedDepth, newDepth);

		checkGdbIsSuspended();	
	}

	private void checkGdbIsSuspended() throws Throwable {
		final IContainerDMContext containerDmc = SyncUtil.getContainerContext();
		Query<Boolean> query = new Query<Boolean>() {
			@Override
			protected void execute(DataRequestMonitor<Boolean> rm) {
				rm.done(fRunCtrl.isSuspended(containerDmc));
			}
		};
		fSession.getExecutor().execute(query);
		
		boolean suspended = query.get(TestsPlugin.massageTimeout(5000), TimeUnit.SECONDS);
		assertTrue("Target is running. It should have been suspended", suspended);
	}

	/**
	 * Perform a stepIntoSelection operation and return the SuspendedEvent indicating the
	 * stepInto has been completed.
	 */
	private ISuspendedDMEvent triggerStepIntoSelection(final IExecutionDMContext exeContext, 
			                                        final String sourceName, 
			                                        final int targetLine, 
			                                        final IFunctionDeclaration function, 
			                                        final boolean skipBreakPoints)
	throws Throwable {
		ServiceEventWaitor<ISuspendedDMEvent> eventWaitor =
				new ServiceEventWaitor<ISuspendedDMEvent>(fSession, ISuspendedDMEvent.class);

		Query<Object> query = new Query<Object>() {
			@Override
			protected void execute(DataRequestMonitor<Object> rm) {
				fRunCtrl.stepIntoSelection(exeContext, sourceName, targetLine, skipBreakPoints, function, rm);
			}
		};
		fSession.getExecutor().execute(query);
		query.get();
		
		return eventWaitor.waitForEvent(TestsPlugin.massageTimeout(10000));			
	}

	/**
	 * Perform a stepIntoSelection operation and return the SuspendedEvent indicating the
	 * stepInto has been completed.
	 */
	private ISuspendedDMEvent triggerRunToLine(final IExecutionDMContext exeContext, 
			                                   final String sourceName, 
			                                   final int targetLine, 
			                                   final boolean skipBreakPoints)
	throws Throwable {
		ServiceEventWaitor<ISuspendedDMEvent> eventWaitor =
				new ServiceEventWaitor<ISuspendedDMEvent>(fSession, ISuspendedDMEvent.class);

		Query<Object> query = new Query<Object>() {
			@Override
			protected void execute(DataRequestMonitor<Object> rm) {
				fRunCtrl.runToLine(exeContext, sourceName, targetLine, skipBreakPoints, rm);
			}
		};
		fSession.getExecutor().execute(query);
		query.get();
		
		return eventWaitor.waitForEvent(TestsPlugin.massageTimeout(10000));			
	}

	/**
	 * This test verifies that we can step into a selection on the same line as where we are currently.
	 */
	@Test
	public void atSameLine() throws Throwable {
        MIStoppedEvent stoppedEvent = SyncUtil.runToLocation("sameLineTest");
        int originalDepth = SyncUtil.getStackDepth(stoppedEvent.getDMContext());

        FunctionDeclaration targetFunction = funcFoo;
        
        // StepInto the method     
        ISuspendedDMEvent suspendedEvent = triggerStepIntoSelection(stoppedEvent.getDMContext(), SRC_FILE, 
        															stoppedEvent.getFrame().getLine(), targetFunction, false);
        
        validateLocation(suspendedEvent, targetFunction.getElementName(), SRC_FILE, FOO_LINE, originalDepth + 1);
	}
	
	/**
	 * This test verifies that we can step into a selection from a later line than where we are currently.
	 */
	@Test
	public void atLaterLine() throws Throwable {
        MIStoppedEvent stoppedEvent = SyncUtil.runToLocation("laterLineTest");
        int originalDepth = SyncUtil.getStackDepth(stoppedEvent.getDMContext());

        FunctionDeclaration targetFunction = funcFoo;
        int line = stoppedEvent.getFrame().getLine() + 3; // The method to stepInto is three lines below the start of the method
        // StepInto the method     
        ISuspendedDMEvent suspendedEvent = triggerStepIntoSelection(stoppedEvent.getDMContext(), SRC_FILE, 
        															line, targetFunction, false);

        validateLocation(suspendedEvent, targetFunction.getElementName(), SRC_FILE, FOO_LINE, originalDepth + 1);
	}

	/**
	 * This test verifies that we can step into a selection of a different file.
	 */
	@Test
	public void atLaterLineOnDifferentFile() throws Throwable {
        MIStoppedEvent stoppedEvent = SyncUtil.runToLocation("laterLineDifferentFileTest");
        int originalDepth = SyncUtil.getStackDepth(stoppedEvent.getDMContext());

        FunctionDeclaration targetFunction = funcValue;
        int line = stoppedEvent.getFrame().getLine() + 1; // The method to stepInto is one line below the start of the method
        // StepInto the method     
        ISuspendedDMEvent suspendedEvent = triggerStepIntoSelection(stoppedEvent.getDMContext(), SRC_FILE, 
        															line, targetFunction, false);

        validateLocation(suspendedEvent, targetFunction.getElementName(), HDR_FILE, VALUE_LINE, originalDepth + 1);
	}
	
	/**
	 * This test verifies that we can step into a selection than has two method calls.
	 * We try to step into the deepest call.
	 */
	@Test
	public void atDoubleMethodDeepCall() throws Throwable {
        MIStoppedEvent stoppedEvent = SyncUtil.runToLocation("doubleMethodTest");
        int originalDepth = SyncUtil.getStackDepth(stoppedEvent.getDMContext());

        FunctionDeclaration targetFunction = funcFoo;
        int line = stoppedEvent.getFrame().getLine() + 1; // The method to stepInto is one line below the start of the method
        // StepInto the method     
        ISuspendedDMEvent suspendedEvent = triggerStepIntoSelection(stoppedEvent.getDMContext(), SRC_FILE, 
        															line, targetFunction, false);

        validateLocation(suspendedEvent, targetFunction.getElementName(), SRC_FILE, FOO_LINE, originalDepth + 1);
	}

	/**
	 * This test verifies that we can step into a selection than has two method calls.
	 * We try to step into the most shallow call.
	 */
	@Test
	public void atDoubleMethodShalowCall() throws Throwable {
        MIStoppedEvent stoppedEvent = SyncUtil.runToLocation("doubleMethodTest");
        int originalDepth = SyncUtil.getStackDepth(stoppedEvent.getDMContext());

        FunctionDeclaration targetFunction = funcBar;
        int line = stoppedEvent.getFrame().getLine() + 1; // The method to stepInto is one line below the start of the method
        // StepInto the method     
        ISuspendedDMEvent suspendedEvent = triggerStepIntoSelection(stoppedEvent.getDMContext(), SRC_FILE, 
        															line, targetFunction, false);

        validateLocation(suspendedEvent, targetFunction.getElementName(), SRC_FILE, BAR_LINE, originalDepth + 1);
	}

	/**
	 * This test verifies that we can step into a recursive method.
	 */
	@Test
	public void recursiveMethod() throws Throwable {
        MIStoppedEvent stoppedEvent = SyncUtil.runToLocation("recursiveTest");
        int finalLine = stoppedEvent.getFrame().getLine();
        int originalDepth = SyncUtil.getStackDepth(stoppedEvent.getDMContext());

        FunctionDeclaration targetFunction = funcRecursive;

        int line = stoppedEvent.getFrame().getLine() + 2; // The method to stepInto is two lines below the start of the method
        // StepInto the method     
        ISuspendedDMEvent suspendedEvent = triggerStepIntoSelection(stoppedEvent.getDMContext(), SRC_FILE, 
        															line, targetFunction, false);
        
        validateLocation(suspendedEvent, targetFunction.getElementName(), SRC_FILE, finalLine, originalDepth + 1);
	}
	
	/**
	 * This test verifies that if we try to step into a selection from an earlier line we will end up
	 * stopping at the first breakpoint that hits.
	 */
	@Test
	public void atPreviousLine() throws Throwable {
		String functionName = "laterLineTest";
        MIStoppedEvent stoppedEvent = SyncUtil.runToLocation(functionName);
        int originalLine = stoppedEvent.getFrame().getLine();
        int originalDepth = SyncUtil.getStackDepth(stoppedEvent.getDMContext());

		// Step past the function call
        stoppedEvent = SyncUtil.step(4, StepType.STEP_OVER); 
        // Set a bp one line below.  We will check that this breakpoint hits when a stepInto is done
        int bpline = originalLine + 4 + 1;
        SyncUtil.addBreakpoint(Integer.toString(bpline));

        FunctionDeclaration targetFunction = funcFoo;
        int line = originalLine + 3; // The method to stepInto is three lines below the start of the method
        
        // StepInto the method     
        ISuspendedDMEvent suspendedEvent = triggerStepIntoSelection(stoppedEvent.getDMContext(), SRC_FILE, 
        															line, targetFunction, false);

        validateLocation(suspendedEvent, functionName, SRC_FILE, bpline, originalDepth);
	}
	
	/**
	 * This test verifies that if we try to step into a selection from a later line that we will not reach, we will end up
	 * stopping at the first breakpoint that hits.
	 */
	@Test
	public void atLaterLineThatIsNotHit() throws Throwable {
		String functionName = "laterLineNotHitTest";
		MIStoppedEvent stoppedEvent = SyncUtil.runToLocation(functionName);
        int originalDepth = SyncUtil.getStackDepth(stoppedEvent.getDMContext());

        FunctionDeclaration targetFunction = funcFoo;
        int line = stoppedEvent.getFrame().getLine() + 2; // The method to stepInto is two lines below the start of the method
        												  // Except we'll never reach it
        // Set a bp a couple of lines below.  We will check that this breakpoint hits and the stepInto is cancelled
        int bpline = line + 2;
        SyncUtil.addBreakpoint(Integer.toString(bpline));

        // StepInto the method     
        ISuspendedDMEvent suspendedEvent = triggerStepIntoSelection(stoppedEvent.getDMContext(), SRC_FILE, 
        															line, targetFunction, false); // Don't skip breakpoints

        validateLocation(suspendedEvent, functionName, SRC_FILE, bpline, originalDepth);

        // Make sure the step to selection operation is no longer active by triggering a run to line before the step into selection line
        suspendedEvent = triggerRunToLine(stoppedEvent.getDMContext(), SRC_FILE, 
										  bpline + 1, false);
        
        validateLocation(suspendedEvent, functionName, SRC_FILE, bpline + 1, originalDepth);        

	}
	
	/**
	 * This test verifies that when specified, we stop at a breakpoint that is hit before the StepIntoSelection
	 * is completed.
	 */
	@Test
	public void atLaterLineStopAtBreakpoint() throws Throwable {
		String functionName = "laterLineTest";
        MIStoppedEvent stoppedEvent = SyncUtil.runToLocation(functionName);
        int originalDepth = SyncUtil.getStackDepth(stoppedEvent.getDMContext());
        int originalLine = stoppedEvent.getFrame().getLine();
        
        // Set a breakpoint before the stepInto line
        SyncUtil.addBreakpoint(Integer.toString(originalLine+1));
        
        int line = originalLine + 3; // The method to stepInto is three lines below the start of the method
        // StepInto the method     
        ISuspendedDMEvent suspendedEvent = triggerStepIntoSelection(stoppedEvent.getDMContext(), SRC_FILE, 
        															line, funcFoo, false);

        validateLocation(suspendedEvent, functionName, SRC_FILE, originalLine + 1, originalDepth);

		// Make sure the step to selection operation is no longer active by triggering a run to line before the step into selection line
        suspendedEvent = triggerRunToLine(stoppedEvent.getDMContext(), SRC_FILE, 
										  originalLine + 2, false);
        
        validateLocation(suspendedEvent, functionName, SRC_FILE, originalLine + 2, originalDepth);        
	}
	
	/**
	 * This test verifies that when specified, we ignore all breakpoints that are hit before the StepIntoSelection
	 * is completed.
	 */
	@Test
	public void atLaterLineSkipBreakpoints() throws Throwable {
        MIStoppedEvent stoppedEvent = SyncUtil.runToLocation("laterLineTest");
        int originalDepth = SyncUtil.getStackDepth(stoppedEvent.getDMContext());
        int originalLine = stoppedEvent.getFrame().getLine();

        // Set two breakpoints before the stepInto line
        SyncUtil.addBreakpoint(Integer.toString(originalLine+1));
        SyncUtil.addBreakpoint(Integer.toString(originalLine+2));
        
        int line = originalLine + 3; // The method to stepInto is three lines below the start of the method

        FunctionDeclaration targetFunction = funcFoo;

        // StepInto the method     
        ISuspendedDMEvent suspendedEvent = triggerStepIntoSelection(stoppedEvent.getDMContext(), SRC_FILE, 
        															line, targetFunction, true);

        validateLocation(suspendedEvent, targetFunction.getElementName(), SRC_FILE, FOO_LINE, originalDepth + 1);
	}
	
	/**
	 * This test verifies that we will not stop at a breakpoint if it is in the middle
	 * of the step-in operations when the run-to-line skip breakpoint option is not selected.
	 */
	@Test
	public void atDoubleMethodStopAtBreakpoint() throws Throwable {
        MIStoppedEvent stoppedEvent = SyncUtil.runToLocation("doubleMethodTest");
        int originalDepth = SyncUtil.getStackDepth(stoppedEvent.getDMContext());

        // Set a breakpoint inside foo, which will hit before our
        // StepInto is finished
        SyncUtil.addBreakpoint(Integer.toString(FOO_LINE));

        FunctionDeclaration targetFunction = funcBar;
        int line = stoppedEvent.getFrame().getLine() + 1; // The method to stepInto is one line below the start of the method
        // StepInto the method     
        ISuspendedDMEvent suspendedEvent = triggerStepIntoSelection(stoppedEvent.getDMContext(), SRC_FILE, 
        															line, targetFunction, false);  // Set not to skip breakpoints, but it should have no effect

        validateLocation(suspendedEvent, targetFunction.getElementName(), SRC_FILE, BAR_LINE, originalDepth + 1);
	}
	
	/**
	 * This test verifies that we will not stop at a breakpoint if it is in the middle
	 * of the step-in operations even if the run-to-line skip breakpoint option is selected. 
	 */
	@Test
	public void atDoubleMethodSkipBreakpoint() throws Throwable {
        MIStoppedEvent stoppedEvent = SyncUtil.runToLocation("doubleMethodTest");
        int originalDepth = SyncUtil.getStackDepth(stoppedEvent.getDMContext());

        // Set a breakpoint inside foo, which will hit before our
        // StepInto is finished
        SyncUtil.addBreakpoint(Integer.toString(FOO_LINE));

        FunctionDeclaration targetFunction = funcBar;
        int line = stoppedEvent.getFrame().getLine() + 1; // The method to stepInto is one line below the start of the method
        // StepInto the method     
        ISuspendedDMEvent suspendedEvent = triggerStepIntoSelection(stoppedEvent.getDMContext(), SRC_FILE, 
        															line, targetFunction, true);  // Set skip breakpoints, which should have non impact

        validateLocation(suspendedEvent, targetFunction.getElementName(), SRC_FILE, BAR_LINE, originalDepth + 1);
	}
	
	/**
	 * This test verifies that if we have two methods with the same name on the same line,
	 * we properly choose the method with the correct number of arguments based on the
	 * step into selection.
	 */
	@Test
	public void diffMethodByArgsNumber() throws Throwable {
        MIStoppedEvent stoppedEvent = SyncUtil.runToLocation("methodWithDiffArgsNumberTest");
        int originalDepth = SyncUtil.getStackDepth(stoppedEvent.getDMContext());

        FunctionDeclaration targetFunction = funcAddWithArg;
        // StepInto the method     
        ISuspendedDMEvent suspendedEvent = triggerStepIntoSelection(stoppedEvent.getDMContext(), SRC_FILE, 
        															stoppedEvent.getFrame().getLine(), targetFunction, false);

        validateLocation(suspendedEvent, targetFunction.getElementName(), SRC_FILE, ADD_WITH_ARG_LINE, originalDepth + 1);
	}

	@Test
	public void diffMethodByArgsNumber2() throws Throwable {
        MIStoppedEvent stoppedEvent = SyncUtil.runToLocation("methodWithDiffArgsNumberTest");
        int originalDepth = SyncUtil.getStackDepth(stoppedEvent.getDMContext());

        FunctionDeclaration targetFunction = funcAddNoArg;
        // StepInto the method     
        ISuspendedDMEvent suspendedEvent = triggerStepIntoSelection(stoppedEvent.getDMContext(), SRC_FILE, 
        															stoppedEvent.getFrame().getLine(), targetFunction, false);

        validateLocation(suspendedEvent, targetFunction.getElementName(), SRC_FILE, ADD_NO_ARG_LINE, originalDepth + 1);
	}
}
