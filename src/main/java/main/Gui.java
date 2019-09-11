package main;

import io.CSV;
import gen.Trajectory;
import gen.Waypoint;
import io.JSON;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Gui {
    @FXML
    public ChoiceBox<String> spline = null;
    public TextField dt = null;
    public TextField velocity = null;
    public TextField acceleration = null;
    public TextField jerk = null;
    public TextField width = null;
    public Slider tightness = null;

    public ListView<String> x = null;
    public ListView<String> y = null;
    public ListView<String> h = null;
    public Button add = null;
    public Button delete = null;

    public Button gen = null;
    public Button save = null;
    public Button load = null;

    private Trajectory trajectory;

    private void addPoint (double x, double y, double h) {
        this.x.getItems().add(Double.toString(x));
        this.y.getItems().add(Double.toString(y));
        this.h.getItems().add(Double.toString(h));
    }

    @FXML
    public void addPoint() {
        Stage prompt = new Stage();
        VBox root = new VBox();
        TextField x = new TextField();
        root.getChildren().add(new HBox(new Label("X Pos: "), x));
        TextField y = new TextField();
        root.getChildren().add(new HBox(new Label("Y Pos: "), y));
        TextField h = new TextField();
        root.getChildren().add(new HBox(new Label("Angle: "), h));
        Button confirm = new Button("OK");

        confirm.setOnAction((e) -> {
            try {
                addPoint(Double.parseDouble(x.getText()),
                        Double.parseDouble(y.getText()),
                        Double.parseDouble(h.getText()));
                prompt.close();
            }catch (NumberFormatException n) {
                System.out.println("not a number!!!");
            }
        });
        x.setOnAction((e) -> y.requestFocus());
        x.setOnKeyPressed((ke) -> { if(y.getText().isEmpty() && ke.getCode().equals(KeyCode.BACK_SPACE)) prompt.close(); });
        y.setOnAction((e) -> h.requestFocus());
        y.setOnKeyPressed((ke) -> { if(y.getText().isEmpty() && ke.getCode().equals(KeyCode.BACK_SPACE)) x.requestFocus(); });
        h.setOnAction((e) -> confirm.fire());
        h.setOnKeyPressed((ke) -> { if(h.getText().isEmpty() && ke.getCode().equals(KeyCode.BACK_SPACE)) y.requestFocus(); });

        root.getChildren().add(confirm);
        root.setSpacing(10);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(20));
        prompt.setScene(new Scene(root));
        prompt.show();
    }

    @FXML
    public void deletePoint() {
        int index = x.getSelectionModel().getSelectedIndices().get(0);
        if(index != -1) {
            x.getItems().remove(index);
            y.getItems().remove(index);
            h.getItems().remove(index);
        }else {
            x.getItems().remove(x.getItems().size()-1);
            y.getItems().remove(y.getItems().size()-1);
            h.getItems().remove(h.getItems().size()-1);
        }
    }

    @FXML
    public void generate() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Comma Separated Values", "*.csv"));
        chooser.setInitialFileName(getDateTimeString());
        File file = chooser.showSaveDialog(new Stage());

        try {
            Waypoint[] waypoints = new Waypoint[x.getItems().size()];
            for(int i = 0; i < waypoints.length; i++) {
                waypoints[i] = new Waypoint(Double.parseDouble(x.getItems().get(i)),
                        Double.parseDouble(y.getItems().get(i)),
                        Math.toRadians(Double.parseDouble(h.getItems().get(i))));
            }
            trajectory = new Trajectory(Trajectory.FitMethod.getMethod(spline.getValue()), 100000, tightness.getValue(),
                    Double.parseDouble(dt.getText()), Double.parseDouble(width.getText()),
                    Double.parseDouble(velocity.getText()), Double.parseDouble(acceleration.getText()), Double.parseDouble(jerk.getText()),
                    waypoints);
            trajectory.generate();

            CSV.exportCSV(file, trajectory);
        }catch(NumberFormatException n) {
            System.out.println("not a number!!!");
        }
    }

    @FXML
    public void save() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("javascript object notation", "*.json"));
        chooser.setInitialFileName(getDateTimeString());
        File file = chooser.showSaveDialog(new Stage());
        // TODO : generate trajectory before saving
        JSON.save(trajectory, file);
    }

    @FXML
    public void load() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("javascript object notation", "*.json"));
        File file = chooser.showOpenDialog(new Stage());
        spline.setValue(trajectory.method.toString());
        trajectory = JSON.load(file);
        dt.setText(Double.toString(trajectory.dt));
        velocity.setText(Double.toString(trajectory.maxVelocity));
        acceleration.setText(Double.toString(trajectory.maxAcceleration));
        jerk.setText(Double.toString(trajectory.maxJerk));
        width.setText(Double.toString(trajectory.wheelBaseWidth));
        tightness.setValue(trajectory.spline.tightness);
        for (Waypoint w : trajectory.spline.waypoints()) {
            addPoint(w.x, w.y, w.heading);
        }
    }

    private String getDateTimeString() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("MMddyyyy_HHmmss");
        LocalDateTime now = LocalDateTime.now();
        return dtf.format(now);
    }

    @FXML
    public void initialize() {
        spline.getItems().add("cubic hermite");
        spline.getItems().add("cubic bezier");
        spline.setValue("cubic hermite");

        x.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            int index = x.getSelectionModel().getSelectedIndices().get(0);
            y.getSelectionModel().select(index);
            h.getSelectionModel().select(index);
        });
        y.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            int index = y.getSelectionModel().getSelectedIndices().get(0);
            x.getSelectionModel().select(index);
            h.getSelectionModel().select(index);
        });
        h.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            int index = h.getSelectionModel().getSelectedIndices().get(0);
            x.getSelectionModel().select(index);
            y.getSelectionModel().select(index);
        });

        StringConverter<String> converter = new StringConverter<String>() {
            @Override
            public String toString(String object) {
                return object;
            }

            @Override
            public String fromString(String string) {
                return string;
            }
        };
        x.setCellFactory(TextFieldListCell.forListView(converter));
        y.setCellFactory(TextFieldListCell.forListView(converter));
        h.setCellFactory(TextFieldListCell.forListView(converter));

        trajectory = null;
    }
}
