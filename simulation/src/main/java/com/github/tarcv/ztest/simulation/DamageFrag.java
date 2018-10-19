package com.github.tarcv.ztest.simulation;

class DamageFrag extends DamageType {
    DamageFrag(Simulation simulation) {
        super(simulation);
        setFlag("NoArmor");
    }
}
