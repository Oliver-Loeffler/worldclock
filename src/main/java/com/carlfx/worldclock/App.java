package com.carlfx.worldclock;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.carlfx.worldclock.WorldClockEvent.*;

/**
 * JavaFX World Clock
 *
 * Creative commons attribution:
 * Font Awesome was used for buttons https://fontawesome.com/license
 */
public class App extends Application {

    private static Scene scene;
    private static Stage stage;
    String [] fontFiles = {
            "Roboto-Regular.ttf",
            "Roboto-Black.ttf",
            "Roboto-Bold.ttf",
            "Roboto-Light.ttf",
            "Roboto-Medium.ttf",
            "RobotoMono-Medium.ttf"
    };

    public static String configFile = "worldclock-config.properties";
    public static VBox clockList;
    @Override
    public void init() throws Exception {
        super.init();
        // load fonts
        Arrays.stream(fontFiles).forEach( f -> fontLoader(f));

        // load locations
        loadLocations();
    }
    private void loadLocations() {
        // detect if file exists?
        // if file is not there then create one add a default city like home
        // if not create fake data
    }
    private void fontLoader(String fileName) {
        Font.loadFont(App.class.getResource(fileName).toExternalForm(), 20);
    }
    private <T> T lookup(Node node, String id) {
       T childNode = (T) node.lookup("#"+id);
       return childNode;
    }
    static ObservableList<Location> locations = FXCollections.observableArrayList();


    @Override
    public void start(Stage stage) throws IOException {
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setOpacity(.75);
        this.stage = stage;

        // fake data
//        locations.addAll(
//                new USLocation("-5", "Pasadena", "MD", 32.0f, Location.TEMP_STD.FAHRENHEIT) ,
//                new USLocation("-8", "Sunnyvale", "CA", 60.0f, Location.TEMP_STD.FAHRENHEIT),
//                new Location("+1", "Amsterdam", "NL", 4.0f, Location.TEMP_STD.CELSIUS) ,
//                new Location("+1", "Münster", "DE",5.0f, Location.TEMP_STD.CELSIUS)
//        );
        // check if file exists
        File file = new File(System.getProperty("user.home") + File.separatorChar + "worldclock");

        if (!file.exists()) {
            file.mkdirs();
        }
        File locationsJson = new File(String.format("%s%s%s%s%s",
                System.getProperty("user.home"),
                File.separatorChar,
                "worldclock",
                File.separatorChar,
                "locations.json"));
        if (locationsJson.exists()) {
            try {
                String actual = Files.readString(locationsJson.toPath());
                if (!"".equals(actual)) {
                    ObjectMapper objectMapper = new ObjectMapper();
                    List<Map<String, Object>> locationArray = objectMapper.readValue(actual, List.class);
                    for (Map<String, Object> map:locationArray) {
                        try {
                            Location location = null;

                            if (map.containsKey("state")) {
                                location = objectMapper.convertValue(map, USLocation.class);
                            } else {
                                location = objectMapper.convertValue(map, Location.class);
                            }
                            locations.add(location);

                        } catch (Throwable th) {
                            th.printStackTrace();
                        }
                    }
                }

                System.out.println("Successfully read from file.");
            } catch (IOException e) {
                System.out.println("An error occurred.");
                e.printStackTrace();
            }
        }

        if (locations.isEmpty()) {
            TimeZone tz = TimeZone.getDefault();
            String offsetId = "" + tz.toZoneId().getRules().getOffset(Instant.now()).getTotalSeconds()/60/60;

            String city = "Home";
            String state = "";

            String countryCode = Locale.getDefault().getCountry();
            if ("US".equalsIgnoreCase(countryCode)) {
                USLocation usLocation = new USLocation(offsetId, city, state, 3.3f, Location.TEMP_STD.CELSIUS);
                locations.add(usLocation);
            } else {
                String zoneId = tz.getID();
                if (zoneId.lastIndexOf("/") > -1) {
                    city = zoneId.substring(zoneId.lastIndexOf("/") + 1);
                } else {
                    city = zoneId;
                }
                locations.add(new Location(offsetId, city, countryCode, 3.3f, Location.TEMP_STD.CELSIUS));
            }
        }

        SimpleLongProperty epochTime = new SimpleLongProperty(new Date().getTime());
        // each tick update epoch property
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.seconds(1), /* every second */
                actionEvent -> epochTime.set(System.currentTimeMillis())) /* update epoch */
        );
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();

        // load each location clock face
        List<Parent> clocks = new ArrayList<>();
        for(Location location:locations) {
            // each controller will attach a listener when epochTime changes.
            clocks.add(loadClockFXML(location, epochTime));
        }
        BorderPane windowContainer = new BorderPane();
        windowContainer.getStyleClass().add("clock-background");
        windowContainer.addEventHandler(WorldClockEvent.MAIN_APP_CLOSE, event -> stage.close());

        // load the window title bar
        windowContainer.getStyleClass().add("window-container");
        clockList = new VBox();
        Parent windowBar = loadWindowControlsFXML(locations);
        BorderPane.setAlignment(windowBar, Pos.CENTER_RIGHT);
        windowContainer.setTop(windowBar);

        // load the config form
        Pane centerPane = new Pane();
        Parent configPane = loadConfigLocationsFXML(locations);
        configPane.setVisible(false);
        centerPane.getChildren().addAll(clockList, configPane);

        windowContainer.setCenter(centerPane);
        makeDraggable(windowContainer);

        // fake map
        ImageView mapImage = new ImageView(new Image(App.class.getResourceAsStream("Mapimage.png")));

        clockList.getStyleClass().add("clock-background");
        clockList.getChildren()
                .addAll(clocks);

        // Animate toggle between Config view vs World Clock List view
        windowContainer.addEventHandler(CONFIG_SHOWING, event -> {
            TranslateTransition moveList = new TranslateTransition();
            moveList.setNode(clockList);
            moveList.setDuration(Duration.millis(400));
            WindowController windowController = event.getPayload();
            if (windowController.isConfigShowing()) {
                moveList.setInterpolator(Interpolator.EASE_OUT);
                moveList.setFromX(0);
                moveList.setToX(-clockList.getWidth());
            } else {
                moveList.setInterpolator(Interpolator.EASE_IN);
                moveList.setFromX(-clockList.getWidth());
                moveList.setToX(0);
            }

            TranslateTransition moveConfig = new TranslateTransition();

            moveConfig.setNode(configPane);

            moveConfig.setDuration(Duration.millis(400));
            if (!windowController.isConfigShowing()) {
                clockList.toFront();
                moveConfig.setInterpolator(Interpolator.EASE_OUT);
                moveConfig.setFromX(0);
                moveConfig.setToX(-clockList.getWidth() + clockList.getPadding().getLeft() + clockList.getPadding().getRight());
            } else {
                configPane.setVisible(true);
                configPane.toFront();
                moveConfig.setInterpolator(Interpolator.EASE_IN);
                moveConfig.setFromX(-clockList.getWidth() + clockList.getPadding().getLeft() + clockList.getPadding().getRight());
                moveConfig.setToX(0);
            }
            System.out.println("clock list  width " + clockList.getWidth());
            System.out.println("config pane width " + configPane.getBoundsInParent().getWidth());
            System.out.println("window bar  width " + windowBar.getBoundsInParent().getWidth());
            System.out.println(" image      width " + mapImage.getBoundsInParent().getWidth());
            moveConfig.playFromStart();
            moveList.playFromStart();
        });

        // Subscribe to a new Location Added event
        windowContainer.addEventHandler(LOCATION_ADD, event -> {
            Location location = event.getPayload();
            System.out.println("addding location please");
            Iterator<Node> itr = clockList.getChildren().iterator();
            int idx = -1;
            boolean found = false;
            while (itr.hasNext()) {
                Location loc = (Location) itr.next().getUserData();
                idx+=1;
                if (loc.equals(location)) {
                    found = true;
                    break;
                }
            }


            try {
                if (found && clockList.getChildren().size()-1 > 0) {
                    // replace with new location
                    clockList.getChildren().set(idx, loadClockFXML(location, epochTime));
                } else {
                    clockList.getChildren().add(loadClockFXML(location, epochTime));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        // Subscribe to a removed Location event
        windowContainer.addEventFilter(LOCATION_REMOVE, event -> {
            Location location = event.getPayload();
            System.out.println("window container location_remove heard!");

            Iterator<Node> itr = clockList.getChildren().iterator();
            while (itr.hasNext()) {
                Location loc = (Location) itr.next().getUserData();
                if (loc.equals(location)) {
                    itr.remove();
                }
            }

            //WorldClockEvent.trigger(clockList, event);
            System.out.println("broadcast out to children");
        });

        // Subscribe to a MOVE UP Location event
        windowContainer.addEventFilter(LOCATION_MOVE_UP, event -> {
            RowLocation rowLocation = event.getPayload();
            System.out.println("window container location_move_up heard! index:" + rowLocation.getIndex() + " loc: " + rowLocation.getLocation().getFullLocationName());
            List<Node> copyList = clockList.getChildren().stream().collect(Collectors.toList());
            clockList.getChildren().removeAll(copyList);
            Node prevNode = copyList.get(rowLocation.getIndex());
            Node currentNode = copyList.get(rowLocation.getIndex() + 1);
            copyList.set(rowLocation.getIndex(), currentNode);
            copyList.set(rowLocation.getIndex()+1, prevNode);
            clockList.getChildren().addAll(copyList);
        });
        // Subscribe to a MOVE UP Location event
        windowContainer.addEventFilter(LOCATION_MOVE_DOWN, event -> {
            RowLocation rowLocation = event.getPayload();
            System.out.println("window container location_move_down heard! index:" + rowLocation.getIndex() + " loc: " + rowLocation.getLocation().getFullLocationName());
            List<Node> copyList = clockList.getChildren().stream().collect(Collectors.toList());
            clockList.getChildren().removeAll(copyList);
            Node nextNode = copyList.get(rowLocation.getIndex());
            Node currentNode = copyList.get(rowLocation.getIndex() - 1);
            copyList.set(rowLocation.getIndex(), currentNode);
            copyList.set(rowLocation.getIndex() - 1, nextNode);
            clockList.getChildren().addAll(copyList);
        });
        windowContainer.setBottom(mapImage);
        scene = new Scene(windowContainer);
        scene.getStylesheets()
             .add(getClass()
             .getResource("styles.css")
             .toExternalForm());
        
        scene.setFill(null);
        stage.setScene(scene);
        stage.show();
    }

    public static class DragContext {
        public Point2D anchorPt;
        public Point2D previousLocation;
    }

    private void makeDraggable(Node node) {
        DragContext dragContext = new DragContext();

        node.addEventHandler(MouseEvent.MOUSE_PRESSED, mouseEvent -> {
            if (dragContext.previousLocation == null) {
                dragContext.previousLocation = new Point2D(stage.getX(), stage.getY());
            }
            dragContext.anchorPt = new Point2D(mouseEvent.getScreenX(), mouseEvent.getScreenY());
        });

        node.addEventHandler(MouseEvent.MOUSE_DRAGGED, mouseEvent -> {
            if (dragContext.anchorPt != null && dragContext.previousLocation != null) {
                stage.setX(dragContext.previousLocation.getX()
                        + mouseEvent.getScreenX()
                        - dragContext.anchorPt.getX());
                stage.setY(dragContext.previousLocation.getY()
                        + mouseEvent.getScreenY()
                        - dragContext.anchorPt.getY());
            }
        });

        node.addEventHandler(MouseEvent.MOUSE_RELEASED, mouseEvent -> {
            dragContext.previousLocation = new Point2D(stage.getX(), stage.getY());
        });
    }

    private static Parent loadClockFXML(Location location, LongProperty epochTime) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource("clock-widget.fxml"));
        Parent parent = fxmlLoader.load();
        parent.setUserData(location);
        WorldClockController controller = fxmlLoader.getController();
        controller.init(location, epochTime);
        return parent;
    }
    private static Parent loadWindowControlsFXML(ObservableList<Location> locations) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource("window-controls.fxml"));
        Parent parent = fxmlLoader.load();
        WindowController controller = fxmlLoader.getController();
        controller.init(locations);
        return parent;
    }
    private static Parent loadConfigLocationsFXML(ObservableList<Location> locations) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource("config-locations.fxml"));
        Parent parent = fxmlLoader.load();
        ConfigLocationsController controller = fxmlLoader.getController();
        controller.init(locations);
        return parent;
    }

    @Override
    public void stop() {
        Platform.exit();
        System.exit(0);
    }

    public static void main(String[] args) {
        System.setProperty("prism.lcdtext", "false");
        launch();
    }
}

/**
 Sunday    10:49
 March 19
 */