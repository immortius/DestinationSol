package com.miloshpetrov.sol2.game;

import com.miloshpetrov.sol2.common.SolMath;
import com.miloshpetrov.sol2.game.gun.GunConfig;
import com.miloshpetrov.sol2.game.gun.GunItem;
import com.miloshpetrov.sol2.game.item.*;
import com.miloshpetrov.sol2.game.maze.MazeConfig;
import com.miloshpetrov.sol2.game.planet.PlanetConfig;
import com.miloshpetrov.sol2.game.planet.SysConfig;
import com.miloshpetrov.sol2.game.projectile.ProjectileConfig;
import com.miloshpetrov.sol2.game.ship.*;

import java.util.List;

public class HardnessCalc {

  public static final float SHIELD_MUL = 1.2f;

  public static float getGunMeanDps(GunConfig gc) {
    ClipConfig cc = gc.clipConf;
    ProjectileConfig pc = cc.projConfig;

    float projDmg = pc.dmg;
    if (pc.emTime > 0) projDmg = 150;
    else if (pc.density > 0) projDmg += 10;

    float projHitChance;
    if (pc.guideRotSpd > 0) {
      projHitChance = .9f;
    } else if (pc.zeroAbsSpd) {
      projHitChance = 0.1f;
    } else {
      projHitChance = (pc.spdLen + pc.acc) / 6;
      if (pc.physSize > 0) projHitChance += pc.physSize;
      projHitChance = SolMath.clamp(projHitChance, .1f, 1);
      if (gc.fixed) {
        projHitChance *= .3f;
      }
    }

    float shotDmg = projDmg * projHitChance;

    return getShotDps(gc, shotDmg);
  }

  public static float getShotDps(GunConfig gc, float shotDmg) {
    ClipConfig cc = gc.clipConf;
    int projectilesPerShot = cc.projectilesPerShot;
    if (gc.timeBetweenShots == 0) projectilesPerShot = cc.size;
    if (projectilesPerShot > 1) shotDmg *= .6f * projectilesPerShot;


    float timeBetweenShots = gc.timeBetweenShots == 0 ? gc.reloadTime : gc.timeBetweenShots;
    return shotDmg / timeBetweenShots;
  }

  private static float getItemCfgDps(ItemConfig ic, boolean fixed) {
    float dps = 0;
    for (SolItem e : ic.examples) {
      if (!(e instanceof GunItem)) throw new AssertionError("all item options must be of the same type");
      GunItem g = (GunItem) e;
      if (g.config.fixed != fixed) {
        String items = "";
        for (SolItem ex : ic.examples) {
          items += ex.getDisplayName() + " ";
        }
        throw new AssertionError("all gun options must have equal fixed param: " + items);
      }
      dps += g.config.meanDps;
    }

    return dps / ic.examples.size() * ic.chance;
  }

  public static float getShipConfDps(ShipConfig sc, ItemMan itemMan) {
    List<ItemConfig> parsed = itemMan.parseItems(sc.items);
    boolean g1Filled = false;
    boolean g2Filled = false;
    float dps = 0;
    for (ItemConfig ic : parsed) {
      SolItem item = ic.examples.get(0);
      if (!(item instanceof GunItem)) continue;
      GunItem g = (GunItem) item;
      if (!g1Filled && sc.hull.m1Fixed == g.config.fixed) {
        dps += getItemCfgDps(ic, g.config.fixed);
        g1Filled = true;
        continue;
      }
      if (sc.hull.g2Pos != null && !g2Filled && sc.hull.m2Fixed == g.config.fixed) {
        dps += getItemCfgDps(ic, g.config.fixed);
        g2Filled = true;
      }
    }
    return dps;
  }

  public static float getShipCfgDmgCap(ShipConfig sc, ItemMan itemMan) {
    List<ItemConfig> parsed = itemMan.parseItems(sc.items);
    float meanShieldLife = 0;
    float meanArmorPerc = 0;
    for (ItemConfig ic : parsed) {
      SolItem item = ic.examples.get(0);
      if (meanShieldLife == 0 && item instanceof Shield) {
        for (SolItem ex : ic.examples) {
          meanShieldLife += ((Shield) ex).getLife();
        }
        meanShieldLife /= ic.examples.size();
        meanShieldLife *= ic.chance;
      }
      if (meanArmorPerc == 0 && item instanceof Armor) {
        for (SolItem ex : ic.examples) {
          meanArmorPerc += ((Armor) ex).getPerc();
        }
        meanArmorPerc /= ic.examples.size();
        meanArmorPerc *= ic.chance;
      }
    }
    return sc.hull.maxLife / (1 - meanArmorPerc) + meanShieldLife * SHIELD_MUL;
  }

  private static float getShipConfListDps(List<ShipConfig> ships) {
    float maxDps = 0;
    for (ShipConfig e : ships) {
      if (maxDps < e.dps) maxDps = e.dps;
    }
    return maxDps;
  }

  public static float getGroundDps(PlanetConfig pc, float grav) {
    float groundDps = getShipConfListDps(pc.groundEnemies);
    float bomberDps = getShipConfListDps(pc.lowOrbitEnemies);
    float res = bomberDps < groundDps ? groundDps : bomberDps;
    float gravFactor = 1 + grav * .5f;
    return res * gravFactor;
  }

  public static float getAtmDps(PlanetConfig pc) {
    return getShipConfListDps(pc.highOrbitEnemies);
  }

  public static float getMazeDps(MazeConfig c) {
    float outer = getShipConfListDps(c.outerEnemies);
    float inner = getShipConfListDps(c.innerEnemies);
    float res = inner < outer ? outer : inner;
    return res * 1.25f;
  }

  public static float getBeltDps(SysConfig c) {
    return 1.2f * getShipConfListDps(c.tempEnemies);
  }

  public static float getSysDps(SysConfig c, boolean inner) {
    return getShipConfListDps(inner ? c.innerTempEnemies : c.tempEnemies);
  }

  private static float getGunDps(GunItem g) {
    if (g == null) return 0;
    return g.config.meanDps;
  }

  public static float getShipDps(SolShip s) {
    ShipHull h = s.getHull();
    return getGunDps(h.getGun(false)) + getGunDps(h.getGun(true));
  }

  public static float getFarShipDps(FarShip s) {
    return getGunDps(s.getGun(false)) + getGunDps(s.getGun(true));
  }

  public static float getShipDmgCap(SolShip s) {
    return getDmgCap(s.getHull().config, s.getArmor(), s.getShield());
  }

  public static float getFarShipDmgCap(FarShip s) {
    return getDmgCap(s.getHullConfig(), s.getArmor(), s.getShield());
  }

  private static float getDmgCap(HullConfig hull, Armor armor, Shield shield) {
    float r = hull.maxLife;
    if (armor != null) r *= 1 / (1 - armor.getPerc());
    if (shield != null) r += shield.getMaxLife() * SHIELD_MUL;
    return r;
  }

  public static boolean isDangerous(float destDmgCap, float dps) {
    float killTime = destDmgCap / dps;
    return killTime < 5;
  }

  public static boolean isDangerous(float destDmgCap, Object srcObj) {
    float dps = getShipObjDps(srcObj);
    return isDangerous(destDmgCap, dps);
  }

  public static float getShipObjDps(Object srcObj) {
    return srcObj instanceof SolShip ? getShipDps((SolShip) srcObj) : getFarShipDps((FarShip) srcObj);
  }
}
