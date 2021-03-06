/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.appinspection.api

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.appinspection.inspector.api.AppInspectorClient
import com.android.tools.idea.appinspection.inspector.api.StubTestAppInspectorClient
import com.android.tools.idea.appinspection.test.AppInspectionServiceRule
import com.android.tools.idea.appinspection.test.AppInspectionTestUtils
import com.android.tools.idea.appinspection.test.INSPECTOR_ID
import com.android.tools.idea.appinspection.test.TEST_JAR
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.idea.transport.faketransport.commands.CommandHandler
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.util.concurrent.CountDownLatch

class AppInspectionDiscoveryTest {
  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer, false)

  private val FAKE_PROCESS = AppInspectionTestUtils.createFakeProcessDescriptor()

  private val ATTACH_HANDLER = object : CommandHandler(timer) {
    override fun handleCommand(command: Commands.Command, events: MutableList<Common.Event>) {
      events.add(
        Common.Event.newBuilder()
          .setKind(Common.Event.Kind.AGENT)
          .setPid(command.pid)
          .setAgentData(Common.AgentData.newBuilder().setStatus(Common.AgentData.Status.ATTACHED).build())
          .build()
      )
    }
  }

  private val grpcServerRule = FakeGrpcServer.createFakeGrpcServer("AppInspectionDiscoveryTest", transportService, transportService)!!
  private val appInspectionServiceRule = AppInspectionServiceRule(timer, transportService, grpcServerRule)

  @get:Rule
  val ruleChain = RuleChain.outerRule(grpcServerRule).around(appInspectionServiceRule)!!

  init {
    transportService.setCommandHandler(Commands.Command.CommandType.ATTACH_AGENT, ATTACH_HANDLER)
    transportService.setCommandHandler(Commands.Command.CommandType.APP_INSPECTION, TestInspectorCommandHandler(timer))
  }

  @Test
  fun launchNewInspector() {
    val discovery =
      AppInspectionDiscovery(appInspectionServiceRule.executorService, appInspectionServiceRule.client)

    discovery.launchInspector(
      AppInspectionDiscoveryHost.LaunchParameters(FAKE_PROCESS, INSPECTOR_ID, TEST_JAR, "testProject"),
      { StubTestAppInspectorClient(it) },
      AppInspectionTestUtils.TestTransportJarCopier,
      appInspectionServiceRule.streamChannel
    ).get()
  }

  @Test
  fun clientIsCached() {
    val discovery =
      AppInspectionDiscovery(appInspectionServiceRule.executorService, appInspectionServiceRule.client)

    // Launch an inspector.
    val firstClient = discovery.launchInspector(
      AppInspectionDiscoveryHost.LaunchParameters(FAKE_PROCESS, INSPECTOR_ID, TEST_JAR, "testProject"),
      { StubTestAppInspectorClient(it) },
      AppInspectionTestUtils.TestTransportJarCopier,
      appInspectionServiceRule.streamChannel
    ).get()

    // Launch same inspector.
    val secondClient = discovery.launchInspector(
      AppInspectionDiscoveryHost.LaunchParameters(FAKE_PROCESS, INSPECTOR_ID, TEST_JAR, "testProject"),
      { StubTestAppInspectorClient(it) },
      AppInspectionTestUtils.TestTransportJarCopier,
      appInspectionServiceRule.streamChannel
    ).get()

    // Check they are the same.
    assertThat(firstClient).isSameAs(secondClient)
  }

  @Test
  fun processTerminationDisposesClient() {
    // Setup
    val discovery = AppInspectionDiscovery(appInspectionServiceRule.executorService, appInspectionServiceRule.client)

    // Launch an inspector client.
    discovery.launchInspector(
      AppInspectionDiscoveryHost.LaunchParameters(FAKE_PROCESS, INSPECTOR_ID, TEST_JAR, "testProject"),
      { StubTestAppInspectorClient(it) },
      AppInspectionTestUtils.TestTransportJarCopier,
      appInspectionServiceRule.streamChannel
    ).get()

    // Verify there is one new client.
    assertThat(discovery.clients).hasSize(1)

    // Set up latch to wait for client disposal.
    // Note: The callback below must use the same executor as discovery service because we rely on the knowledge that the single threaded
    // executor will execute them AFTER executing the system's cleanup code. Otherwise, the latch may get released too early due to race,
    // and cause the test to flake.
    val clientDisposedLatch = CountDownLatch(1)
    discovery.clients.values.first().get().addServiceEventListener(object : AppInspectorClient.ServiceEventListener {
      override fun onDispose() {
        clientDisposedLatch.countDown()
      }
    }, appInspectionServiceRule.executorService)

    // Fake target termination to dispose of client.
    transportService.addEventToStream(
      FakeTransportService.FAKE_DEVICE_ID,
      Common.Event.newBuilder()
        .setTimestamp(timer.currentTimeNs)
        .setKind(Common.Event.Kind.PROCESS)
        .setGroupId(FakeTransportService.FAKE_PROCESS.pid.toLong())
        .setPid(FakeTransportService.FAKE_PROCESS.pid)
        .setIsEnded(true)
        .build()
    )

    // Wait and verify
    clientDisposedLatch.await()
    assertThat(discovery.clients).isEmpty()
  }

  @Test
  fun disposeClients() {
    val discovery = AppInspectionDiscovery(appInspectionServiceRule.executorService, appInspectionServiceRule.client)

    discovery.launchInspector(
      AppInspectionDiscoveryHost.LaunchParameters(FAKE_PROCESS, INSPECTOR_ID, TEST_JAR, "project_to_dispose"),
      { StubTestAppInspectorClient(it) },
      AppInspectionTestUtils.TestTransportJarCopier,
      appInspectionServiceRule.streamChannel
    ).get()

    discovery.launchInspector(
      AppInspectionDiscoveryHost.LaunchParameters(FAKE_PROCESS, INSPECTOR_ID, TEST_JAR, "other_project"),
      { StubTestAppInspectorClient(it) },
      AppInspectionTestUtils.TestTransportJarCopier,
      appInspectionServiceRule.streamChannel
    ).get()

    assertThat(discovery.clients).hasSize(2)

    discovery.disposeClients("project_to_dispose")

    assertThat(discovery.clients).hasSize(1)
    assertThat(discovery.clients.keys.first().projectName).isEqualTo("other_project")
  }

  @Test
  fun removeProcess() {
    val discovery = AppInspectionDiscovery(appInspectionServiceRule.executorService, appInspectionServiceRule.client)

    val terminatedProcess = FakeTransportService.FAKE_PROCESS.toBuilder().setName("terminate").setPid(1).build()
    val terminateProcessDescriptor = AppInspectionTestUtils.createFakeProcessDescriptor(process = terminatedProcess)

    val otherProcess = FakeTransportService.FAKE_PROCESS.toBuilder().setName("other").setPid(2).build()
    val otherProcessDescriptor = AppInspectionTestUtils.createFakeProcessDescriptor(process = otherProcess)

    discovery.launchInspector(
      AppInspectionDiscoveryHost.LaunchParameters(terminateProcessDescriptor, INSPECTOR_ID, TEST_JAR, "project"),
      { StubTestAppInspectorClient(it) },
      AppInspectionTestUtils.TestTransportJarCopier,
      appInspectionServiceRule.streamChannel
    ).get()

    discovery.launchInspector(
      AppInspectionDiscoveryHost.LaunchParameters(otherProcessDescriptor, INSPECTOR_ID, TEST_JAR, "project"),
      { StubTestAppInspectorClient(it) },
      AppInspectionTestUtils.TestTransportJarCopier,
      appInspectionServiceRule.streamChannel
    ).get()

    assertThat(discovery.clients).hasSize(2)
    assertThat(discovery.targets).hasSize(2)

    discovery.removeProcess(terminateProcessDescriptor)

    assertThat(discovery.targets).hasSize(1)
    assertThat(discovery.clients).hasSize(1)
    assertThat(discovery.clients.keys.first().processDescriptor).isSameAs(otherProcessDescriptor)
  }
}