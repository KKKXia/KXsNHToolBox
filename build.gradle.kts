
plugins {
    id("com.github.ElytraServers.elytra-conventions") version "v1.1.2"
    id("com.gtnewhorizons.gtnhconvention")
}

minecraft {
    extraRunJvmArguments.add("-Xmx8G")
    extraRunJvmArguments.add("-Xms8G")
}
