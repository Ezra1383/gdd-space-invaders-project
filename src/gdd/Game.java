package gdd;

import gdd.scene.Scene1;
import gdd.scene.TitleScene;
import javax.swing.JFrame;

public class Game extends JFrame  {

    TitleScene titleScene;
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
        scene1 = new Scene1(this);
        initUI();
        loadTitle();
    }

    private void initUI() {

        setTitle("Space Invaders");
        setSize(Global.BOARD_WIDTH, Global.BOARD_HEIGHT);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);
        setLocationRelativeTo(null);

    }

    public void loadTitle() {
        getContentPane().removeAll();
        // add(new Title(this));
        add(titleScene);
        titleScene.start();
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