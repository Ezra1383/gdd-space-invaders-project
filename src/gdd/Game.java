package gdd;

import gdd.scene.AboutScene;
import gdd.scene.Scene1;
import gdd.scene.TitleScene;
import java.awt.Dimension;
import javax.swing.JFrame;

public class Game extends JFrame  {

    TitleScene titleScene;
    AboutScene aboutScene;
    Scene1 scene1;

    public Game() {
        // Decode the ship animations in the background while the title screen is
        // up, so gameplay never stalls on a first-spawn sprite load.
        Thread preload = new Thread(() -> {
            Background.preload();      // quick, and needed as soon as play starts
            GifSprites.preload();
            Background.preloadWrecks(); // derives from the ship art, so must follow
        }, "sprite-preload");
        preload.setDaemon(true);
        preload.start();

        titleScene = new TitleScene(this);
        aboutScene = new AboutScene(this);
        scene1 = new Scene1(this);
        initUI();
        loadTitle();
    }

    private void initUI() {

        setTitle("Reversal");

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);

        // setSize() sizes the whole window — title bar and borders included —
        // which left the content pane 39px shorter and 16px narrower than the
        // board, clipping anything drawn near the bottom or right edge (the
        // boss HP bar sat entirely in the lost strip). Size the content pane
        // and let pack() add the decorations on top.
        getContentPane().setPreferredSize(
                new Dimension(Global.BOARD_WIDTH, Global.BOARD_HEIGHT));
        pack();

        setLocationRelativeTo(null);

    }

    public void loadTitle() {
        getContentPane().removeAll();
        // add(new Title(this));
        add(titleScene);
        aboutScene.stop();
        titleScene.start();
        revalidate();
        repaint();
    }

    /** The credits screen, reached with A from the title. */
    public void loadAbout() {
        getContentPane().removeAll();
        add(aboutScene);
        titleScene.pause(); // the title music carries on under the credits
        aboutScene.start();
        revalidate();
        repaint();
    }

    public void loadScene1() {
        // ....
    }

    public void loadScene2() {
        getContentPane().removeAll();
        add(scene1);
        titleScene.stop();
        scene1.start();
        revalidate();
        repaint();
    }
}