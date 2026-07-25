package gdd.sprite;

import java.awt.Image;
import java.awt.Rectangle;

abstract public class Sprite {

    protected boolean visible;
    protected Image image;
    protected boolean dying;
    protected int visibleFrames = 10;

    protected int x;
    protected int y;
    protected int dx;
    protected int dy;

    public Sprite() {
        visible = true;
    }

    abstract public void act();

    /**
     * The rectangle used when something checks whether it has struck this
     * sprite. Defaults to the full image; {@link Player} shrinks it to a small
     * centred dot so danmaku is dodged on a fair grazing hitbox, not the whole
     * hull.
     */
    public Rectangle getHitbox() {
        return new Rectangle(x, y, getImage().getWidth(null), getImage().getHeight(null));
    }

    public boolean collidesWith(Sprite other) {
        if (other == null || !this.isVisible() || !other.isVisible()) {
            return false;
        }
        return this.getX() < other.getX() + other.getImage().getWidth(null)
                && this.getX() + this.getImage().getWidth(null) > other.getX()
                && this.getY() < other.getY() + other.getImage().getHeight(null)
                && this.getY() + this.getImage().getHeight(null) > other.getY();
    }

    public void die() {
        visible = false;
    }

    public boolean isVisible() {
        return visible;
    }

    public void visibleCountDown() {
        if (visibleFrames > 0) {
            visibleFrames--;
        } else {
            visible = false;
        }
    }

    protected void setVisible(boolean visible) {
        this.visible = visible;
    }

    public void setImage(Image image) {
        this.image = image;
    }

    public Image getImage() {
        return image;
    }

    public void setX(int x) {
        this.x = x;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getY() {
        return y;
    }

    public int getX() {
        return x;
    }

    public void setDying(boolean dying) {
        this.dying = dying;
    }

    public boolean isDying() {
        return this.dying;
    }
}
