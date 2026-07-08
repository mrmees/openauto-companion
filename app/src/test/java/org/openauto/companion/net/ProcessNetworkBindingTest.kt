package org.openauto.companion.net

import android.net.Network
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessNetworkBindingTest {
    @Test
    fun bindForScope_bindsTargetNetworkAndRestoresPreviousNetwork() {
        val previous = testNetwork()
        val target = testNetwork()
        val binder = FakeProcessNetworkBinder(current = previous)
        val binding = ProcessNetworkBinding(binder)

        val result = binding.bindForScope(target)

        assertTrue(result is ProcessNetworkBinding.Result.Bound)
        assertSame(target, binder.boundNetworks.single())

        val restored = (result as ProcessNetworkBinding.Result.Bound).binding.restore()

        assertTrue(restored)
        assertBoundNetworks(binder, target, previous)
    }

    @Test
    fun bindForScope_restoresNullWhenProcessWasUnbound() {
        val target = testNetwork()
        val binder = FakeProcessNetworkBinder(current = null)
        val binding = ProcessNetworkBinding(binder)

        val result = binding.bindForScope(target)
        val restored = (result as ProcessNetworkBinding.Result.Bound).binding.restore()

        assertTrue(restored)
        assertBoundNetworks(binder, target, null)
    }

    @Test
    fun bindForScope_returnsNoNetworkWhenTargetNetworkMissing() {
        val binder = FakeProcessNetworkBinder(current = testNetwork())
        val binding = ProcessNetworkBinding(binder)

        val result = binding.bindForScope(null)

        assertSame(ProcessNetworkBinding.Result.NoNetwork, result)
        assertTrue(binder.boundNetworks.isEmpty())
    }

    @Test
    fun bindForScope_returnsFailedWhenAndroidRejectsBinding() {
        val target = testNetwork()
        val binder = FakeProcessNetworkBinder(current = null, bindResult = false)
        val binding = ProcessNetworkBinding(binder)

        val result = binding.bindForScope(target)

        assertTrue(result is ProcessNetworkBinding.Result.Failed)
        assertBoundNetworks(binder, target)
    }

    @Test
    fun bindForScope_returnsFailedWhenBinderThrows() {
        val target = testNetwork()
        val failure = IllegalStateException("bind failed")
        val binder = FakeProcessNetworkBinder(current = null, bindFailure = failure)
        val binding = ProcessNetworkBinding(binder)

        val result = binding.bindForScope(target)

        assertTrue(result is ProcessNetworkBinding.Result.Failed)
        assertSame(failure, (result as ProcessNetworkBinding.Result.Failed).cause)
        assertBoundNetworks(binder, target)
    }

    private fun testNetwork(): Network {
        val constructor = Network::class.java.getDeclaredConstructor()
        constructor.isAccessible = true
        return constructor.newInstance()
    }

    private fun assertBoundNetworks(
        binder: FakeProcessNetworkBinder,
        vararg expected: Network?
    ) {
        assertTrue(
            "Expected ${expected.size} bound network calls, got ${binder.boundNetworks.size}",
            binder.boundNetworks.size == expected.size
        )
        expected.forEachIndexed { index, network ->
            assertSame(network, binder.boundNetworks[index])
        }
    }

    private class FakeProcessNetworkBinder(
        private val current: Network?,
        private val bindResult: Boolean = true,
        private val bindFailure: RuntimeException? = null
    ) : ProcessNetworkBinder {
        val boundNetworks = mutableListOf<Network?>()

        override fun current(): Network? = current

        override fun bind(network: Network?): Boolean {
            boundNetworks += network
            bindFailure?.let { throw it }
            return bindResult
        }
    }
}
