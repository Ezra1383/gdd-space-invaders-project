package gdd.powerup;

import static gdd.Global.*;
import gdd.Images;
import gdd.sprite.Player;

/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */


public class SpeedUp extends PowerUp {

    public SpeedUp(int x, int y) {
        super(x, y);
        // Loaded via Images (ImageIO-backed) to dodge AWT's buggy PNG scaling.
        setImage(Images.load(IMG_POWERUP_SPEEDUP));
    }

    public void act() {
        // SpeedUp specific behavior can be added here
        // For now, it just drifts left with the scroll
        this.x -= 2; // Move left by 2 pixels each frame
    }

    public void upgrade(Player player) {
        // Upgrade the player with speed boost
        player.setSpeed(player.getSpeed() + 4); // Increase player's speed by 1
        this.die(); // Remove the power-up after use
    }

}
