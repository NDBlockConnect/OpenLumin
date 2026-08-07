/**
 * OpenLumin 版本配置中心
 * 统一管理所有 Minecraft 版本和加载器版本映射
 */
object Versions {
    const val OPENLUMIN = "1.0.0"
    const val JAVA = 21

    // Minecraft 版本映射
    object Minecraft {
        // 2026.x 新版本体系
        const val V26_2 = "26.2"
        const val V26_1 = "26.1.2"

        // 1.21.x
        const val V1_21_10 = "1.21.10"
        const val V1_21_4 = "1.21.4"
        const val V1_21_3 = "1.21.3"
        const val V1_21_1 = "1.21.1"

        // 1.20.x
        const val V1_20_6 = "1.20.6"
        const val V1_20_5 = "1.20.5"
        const val V1_20_4 = "1.20.4"
        const val V1_20_2 = "1.20.2"
        const val V1_20_1 = "1.20.1"

        // 1.19.x
        const val V1_19_4 = "1.19.4"
        const val V1_19_3 = "1.19.3"
        const val V1_19_2 = "1.19.2"

        // 1.18.x
        const val V1_18_2 = "1.18.2"

        // 1.17.x
        const val V1_17_1 = "1.17.1"

        // 1.16.x
        const val V1_16_5 = "1.16.5"

        // 1.15.x
        const val V1_15_2 = "1.15.2"

        // 1.14.x
        const val V1_14_4 = "1.14.4"

        // 1.13.x
        const val V1_13_2 = "1.13.2"
    }

    // NeoForge 版本映射
    object NeoForge {
        const val V26_2 = "26.2.0"
        const val V26_1 = "26.1.2.94"
        const val V1_21_10 = "21.10.0"
        const val V1_21_4 = "21.4.27-beta"
        const val V1_21_3 = "21.3.0-beta"
        const val V1_21_1 = "21.1.0"
        const val V1_20_6 = "20.6.0"
        const val V1_20_5 = "20.5.0"
        const val V1_20_4 = "20.4.237"
        const val V1_20_2 = "20.2.88"
        const val GRADLE_PLUGIN = "7.0.163"
    }

    // Fabric 版本映射
    object Fabric {
        const val LOADER = "0.19.3"
        const val API_26_1 = "0.155.2+26.1.2"
        const val API_1_21 = "0.100.0+1.21"
        const val API_1_20 = "0.92.2+1.20.1"
        const val API_1_19 = "0.76.0+1.19.2"
        const val API_1_18 = "0.73.0+1.18.2"
        const val API_1_17 = "0.42.0+1.17"
        const val API_1_16 = "0.42.0+1.16"
        const val API_1_15 = "0.28.5+1.15"
        const val API_1_14 = "0.28.5+1.14"
        const val API_1_13 = "0.28.5+1.13"
        const val LOOM = "1.17.17"
    }

    // 依赖库版本
    object Deps {
        const val JSR305 = "3.0.2"
        const val ANNOTATIONS = "24.0.1"
        const val JOML = "1.10.5"
    }
}
