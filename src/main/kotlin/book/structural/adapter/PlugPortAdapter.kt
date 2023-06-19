package book.structural.adapter

interface USPlug {
    val hasPower: Int
}

interface EUPlug {
    val hasPower: String // "TRUE" or "FALSE"
}

interface UsbMini {
    val hasPower: Power
}

enum class Power {
    TRUE, FALSE
}

interface UsbTypeC {
    val hasPower: Boolean
}

fun cellPhone(chargeCable: UsbTypeC) {
    if (chargeCable.hasPower) {
        println("I've Got The Power")
    } else {
        println("No Power")
    }
}

// Power outlet exposes USPlug interface
fun usPowerOutlet(): USPlug {
    return object : USPlug {
        override val hasPower = 1
    }
}

// Charger accepts EUPlug interface and exposes UsbMini
fun charger(plug: EUPlug): UsbMini {
    return object : UsbMini {
        override val hasPower = Power.valueOf(plug.hasPower)
    }
}

/**
 * 확장 함수를 사용해서 타입 변경
 */
fun USPlug.toEUPlug(): EUPlug {
    val hasPower = if (this.hasPower == 1) "TRUE" else "FALSE"

    return object : EUPlug {
        override val hasPower = hasPower
    }
}

fun UsbMini.toUsbTypeC(): UsbTypeC {
    val hasPower = this.hasPower == Power.TRUE

    return object : UsbTypeC {
        override val hasPower = hasPower
    }
}

fun main() {
    // Type missmatch 발생
//    cellPhone(
//        charger(
//            usPowerOutlet()
//        )
//    )

    cellPhone(
        charger(
            usPowerOutlet().toEUPlug()
        ).toUsbTypeC()
    )
}