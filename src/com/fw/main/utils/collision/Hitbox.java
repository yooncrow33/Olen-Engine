package com.fw.main.utils.collision;

import com.fw.internal.utils.Internal;
import com.fw.internal.utils.InternalUtils;
import com.fw.main.Fw;

import java.awt.*;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Hitbox {
    private int x,y,width,height;
    public void setX(int x) { this.x = x; }
    public void setY(int y) { this.y = y; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public AtomicBoolean use = new AtomicBoolean(false);
    public final boolean centeringAtDynamicScaling;
    public final Integer id = getId();

    private static final AtomicInteger ID_COUNTER = new AtomicInteger(1);

    private static Integer getId() {
        return ID_COUNTER.getAndIncrement();
    }

    @Deprecated
    public Hitbox(Builder builder) {
        x = builder.x;
        y = builder.y;
        width = builder.width;
        height = builder.height;
        Fw.Debugger.Internal.addObject(this);
        this.centeringAtDynamicScaling = builder.centeringAtDynamicScaling;
        use.set(builder.initIsUse);
        if (builder.initIsUse) {
            Fw.Debugger.Internal.enableObject(this);
        } else {
            Fw.Debugger.Internal.disableObject(this);
        }
    }

    public void renderHitbox(Graphics g) {
        if (!use.get()) {
            System.err.println("this object is disable!");
            return;
        }
        g.setColor(Color.green);
        Graphics2D g2 = (Graphics2D) g;
        g2.setStroke(InternalUtils.getBasicStroke());
        g2.drawRect(x,y,width,height);
    }

    public boolean intersects(Hitbox other) {
        if (!use.get()) {
            System.err.println("this object is disable!");
            return false;
        }
        if (other.id.equals(this.id)) {
            System.err.println("Intersection with itself");
            return false;
        } else {
            return this.x < other.x + other.width &&
                    this.x + this.width > other.x &&
                    this.y < other.y + other.height &&
                    this.y + this.height > other.y;
        }
    }

    public boolean contains(int x, int y) {
        if (!use.get()) {
            System.err.println("this object is disable!");
            return false;
        }
        return this.x <= x &&
                this.x + this.width >= x &&
                this.y <= y &&
                this.y + this.height >= y;
    }

    public void Free() {
        Fw.Debugger.Internal.freeObject(this);
    }

    public void Disable() {
        Fw.Debugger.Internal.disableObject(this);
        use.set(false);
    }

    public void Enable() {
        Fw.Debugger.Internal.enableObject(this);
        use.set(true);
    }

    public void setWidth(int width) {
        int lastWidth = this.width;
        this.width = width;
        if (centeringAtDynamicScaling) {
            x = x - (width - lastWidth)/2;
        }
    }

    public void setHeight(int height) {
        int lastHeight = this.height;
        this.height = height;
        if (centeringAtDynamicScaling) {
            y = y - (height - lastHeight) / 2;
        }
    }

    public static class Builder {
        private int x = 10;
        private int y = 10;
        private int width = 10;
        private int height = 10;
        boolean initIsUse = true;
        boolean centeringAtDynamicScaling = true;

        public Builder setX(int x) {
            this.x = x;
            return this;
        }

        public Builder setY(int y) {
            this.y = y;
            return this;
        }

        public Builder setWidth(int width) {
            this.width = width;
            return this;
        }

        public Builder setHeight(int height) {
            this.height = height;
            return this;
        }

        public Builder setInitUse(boolean b) {
            this.initIsUse = b;
            return this;
        }

        public Builder setCenteringAtDynamicScaling(boolean b) {
            this.centeringAtDynamicScaling = b;
            return this;
        }
        public Hitbox build() {
            return new Hitbox(this);
        }
    }
}
