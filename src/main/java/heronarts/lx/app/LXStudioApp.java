/**
 * Copyright 2020- Mark C. Slee, Heron Arts LLC
 *
 * This file is part of the LX Studio software library. By using
 * LX, you agree to the terms of the LX Studio Software License
 * and Distribution Agreement, available at: http://lx.studio/license
 *
 * Please note that the LX license is not open-source. The license
 * allows for free, non-commercial use.
 *
 * HERON ARTS MAKES NO WARRANTY, EXPRESS, IMPLIED, STATUTORY, OR
 * OTHERWISE, AND SPECIFICALLY DISCLAIMS ANY WARRANTY OF
 * MERCHANTABILITY, NON-INFRINGEMENT, OR FITNESS FOR A PARTICULAR
 * PURPOSE, WITH RESPECT TO THE SOFTWARE.
 *
 * @author Mark C. Slee <mark@heronarts.com>
 */

package heronarts.lx.app;

import java.io.File;
import java.util.logging.Logger;
import java.util.List;
import java.util.ArrayList;

import heronarts.lx.LX;
import heronarts.lx.LXPlugin;
import heronarts.lx.studio.LXStudio;
import processing.core.PApplet;
import processing.core.PImage;
import processing.core.PVector;
import processing.core.PMatrix3D;
// import processing.video.Movie;
import flavius.ledportal.LPSimConfig;
import flavius.ledportal.LPStructure;
import flavius.ledportal.LPMeshable;
import heronarts.lx.app.ui.UIWireframe;
import heronarts.lx.app.ui.UIAxes;
import heronarts.lx.app.ui.UIVideoFrame;
import heronarts.p3lx.ui.UI.CoordinateSystem;

/**
 * This is an example top-level class to build and run an LX Studio
 * application via an IDE. The main() method of this class can be
 * invoked with arguments to either run with a full Processing 3 UI
 * or as a headless command-line only engine.
 */
public class LXStudioApp extends PApplet implements LXPlugin {

  private static final String WINDOW_TITLE = "LEDPortal";

  private static int WIDTH = 1280;
  private static int HEIGHT = 800;
  private static boolean FULLSCREEN = false;

  private static final Logger logger = Logger.getLogger(PApplet.class.getName());
  LPSimConfig config;
//   Movie movie;
  PImage videoFrame;

  PMatrix3D flattener;
  PMatrix3D unflattener;
  float[][] flatBounds;
  float[][] modelBounds;

  @Override
  public void settings() {
    if (FULLSCREEN) {
      fullScreen(PApplet.P3D);
    } else {
      size(WIDTH, HEIGHT, PApplet.P3D);
    }
  }

  @Override
  public void setup() {

    config = new LPSimConfig();
    config.updateFromJSONObject(loadJSONObject(config.activeModel));
    for (String activeStructure : config.activeStructures) {
      config.updateFromJSONObject(loadJSONObject(activeStructure));
    }

	flattener = config.getWorldFlattener();
	unflattener = config.getWorldUnflattener();
	modelBounds = config.getModelBounds();
	flatBounds = config.getModelFlatBounds();

    LXStudio.Flags flags = new LXStudio.Flags(this);
    flags.resizable = true;
    flags.useGLPointCloud = false;
    flags.startMultiThreaded = true;

    new LXStudio(this, flags, config.getModel());
    this.surface.setTitle(WINDOW_TITLE);
  }

  void videoSetup() {
    if (config.activeImage != null) {
      videoFrame = loadImage(config.activeImage);
    } else if (config.activeMovie != null) {
    //   movie = new Movie((PApplet) this, config.activeMovie);
    //   movie.loop();
    //   while (!movie.available());
    //   movie.read();
    //   videoFrame = createImage(movie.width, movie.height, RGB);
    } else if (config.screencapBounds != null) {
      // activeScreen =
      // GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
      // int activeScreenWidth = activeScreen.getDisplayMode().getWidth();
      // int activeScreenHeight = activeScreen.getDisplayMode().getHeight();
      // logger.info(String.format(
      // "active screen dimensions: [%d, %d]", activeScreenWidth,
      // activeScreenHeight));
      // screencapRectangle = new Rectangle(
      // int(screencapBounds[0] * activeScreenWidth),
      // int(screencapBounds[1] * activeScreenHeight),
      // int(screencapBounds[2] * activeScreenWidth),
      // int(screencapBounds[3] * activeScreenHeight)
      // );
      // logger.info(String.format(
      // "screencap rectangle: %s", screencapRectangle));
      // try {
      // robot = new Robot(activeScreen);
      // } catch (Exception e) {
      // logger.warning(e.getMessage());
      // }
      // BufferedImage screenBuffer =
      // robot.createScreenCapture(screencapRectangle);
      // videoFrame = new PImage(screenBuffer);
    }
    if(videoFrame != null)
      logger.info(String.format("videoFrame: %d x %d", videoFrame.width,
        videoFrame.height));
  }

  @Override
  public void initialize(LX lx) {
    // Here is where you should register any custom components or make modifications
    // to the LX engine or hierarchy. This is also used in headless mode, so note that
    // you cannot assume you are working with an LXStudio class or that any UI will be
    // available.

    // Register custom pattern and effect types
    lx.registry.addPattern(heronarts.lx.app.pattern.AppPattern.class);
    lx.registry.addEffect(heronarts.lx.app.effect.AppEffect.class);
  }

  public void initializeUI(LXStudio lx, LXStudio.UI ui) {
    // Here is where you may modify the initial settings of the UI before it is fully
    // built. Note that this will not be called in headless mode. Anything required
    // for headless mode should go in the raw initialize method above.

    ui.setCoordinateSystem(CoordinateSystem.valueOf("RIGHT_HANDED"));
  }

  public void onUIReady(LXStudio lx, LXStudio.UI ui) {
    // At this point, the LX Studio application UI has been built. You may now add
    // additional views and components to the Ui heirarchy.

    for (LPStructure structure : config.structures) {
      ui.preview.addComponent(new UIWireframe(structure));
    }
    for (LPStructure debugStructure : config.debugStructures) {
      ui.preview.addComponent(new UIWireframe(debugStructure, 0xff0000));
    }

    ui.preview.addComponent(new UIAxes());

    onUIReadyMovie(lx, ui);
  }

  void onUIReadyMovie(LXStudio lx, LXStudio.UI ui) {
    if (videoFrame == null)
      videoSetup();
    if (videoFrame == null)
      return;

    List<float[]> vertexUVPairs = new ArrayList<float[]>();

    vertexUVPairs
      .add(new float[] { flatBounds[0][0], flatBounds[1][0], 0, 0, 0 });
    vertexUVPairs.add(new float[] { flatBounds[0][1], flatBounds[1][0], 0,
      videoFrame.width, 0 });
    vertexUVPairs.add(new float[] { flatBounds[0][1], flatBounds[1][1], 0,
      videoFrame.width, videoFrame.height });
    vertexUVPairs.add(new float[] { flatBounds[0][0], flatBounds[1][1], 0, 0,
      videoFrame.height });
    for (float[] vertexUVPair : vertexUVPairs) {
      PVector uvPosition = new PVector(vertexUVPair[0], vertexUVPair[1],
        vertexUVPair[2]);
      PVector unflattened = LPMeshable.coordinateTransform(unflattener,
        uvPosition);
      vertexUVPair[0] = unflattened.x;
      vertexUVPair[1] = unflattened.y;
      vertexUVPair[2] = unflattened.z;
      logger.fine(String.format("unflattened uv position %s to %s",
        LPMeshable.formatPVector(uvPosition),
        LPMeshable.formatPVector(unflattened)));
    }
    ui.preview.addComponent(new UIVideoFrame(vertexUVPairs, videoFrame));
  }

  @Override
  public void draw() {
    // All handled by core LX engine, do not modify, method exists only so that Processing
    // will run a draw-loop.
  }

  /**
   * Main interface into the program. Two modes are supported, if the --headless
   * flag is supplied then a raw CLI version of LX is used. If not, then we embed
   * in a Processing 3 applet and run as such.
   *
   * @param args Command-line arguments
   */
  public static void main(String[] args) {
    LX.log("Initializing LX version " + LXStudio.VERSION);
    boolean headless = false;
    File projectFile = null;
    for (int i = 0; i < args.length; ++i) {
      if ("--help".equals(args[i]) || "-h".equals(args[i])) {
      } else if ("--headless".equals(args[i])) {
        headless = true;
      } else if ("--fullscreen".equals(args[i]) || "-f".equals(args[i])) {
        FULLSCREEN = true;
      } else if ("--width".equals(args[i]) || "-w".equals(args[i])) {
        try {
          WIDTH = Integer.parseInt(args[++i]);
        } catch (Exception x ) {
          LX.error("Width command-line argument must be followed by integer");
        }
      } else if ("--height".equals(args[i]) || "-h".equals(args[i])) {
        try {
          HEIGHT = Integer.parseInt(args[++i]);
        } catch (Exception x ) {
          LX.error("Height command-line argument must be followed by integer");
        }
      } else if (args[i].endsWith(".lxp")) {
        try {
          projectFile = new File(args[i]);
        } catch (Exception x) {
          LX.error(x, "Command-line project file path invalid: " + args[i]);
        }
      }
    }
    if (headless) {
      // We're not actually going to run this as a PApplet, but we need to explicitly
      // construct and set the initialize callback so that any custom components
      // will be run
      LX.Flags flags = new LX.Flags();
      flags.initialize = new LXStudioApp();
      if (projectFile == null) {
        LX.log("WARNING: No project filename was specified for headless mode!");
      }
      LX.headless(flags, projectFile);
    } else {
      PApplet.main("heronarts.lx.app.LXStudioApp", args);
    }
  }

}
