package com.example.demo1;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Cursor;
import javafx.scene.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;
import javafx.stage.Stage;
import java.awt.*;

import static java.lang.Math.PI;


public class HelloApplication extends Application {
    int xCenterPosition = 0, yCenterPosition = 0;
    // Location of center of the window
    private double rotation = 0;
    // Horizontal rotation of camera in values <-1;1>, used to calculate the direction of movement
    private final Rotate rotationX = new Rotate(0, Rotate.X_AXIS), rotationY = new Rotate(0, Rotate.Y_AXIS);
    // Rotation objets used to rotate camera horizontally and vertically
    private final double mouseSensitivity = 0.1;
    // Camera rotation speed multiplier
    private final double movementSpeed = 7;
    // Adjust this to change the player movement speed in [m/s]
    private boolean movingForward = false, movingBackward = false, movingLeft = false, movingRight = false, isShiftDown= false, isControlDown = false, isJumping = false;
    // Boolean flags that determine if player is moving in any direction with WASD, is holding shift for sprinting, is holding ctrl for crouching and is jumping is a value that is set to true upon hitting space and is true while the jump animation is playing
    private double movementSmoothing = 0;
    // Used to smooth out the movement, rumps up to 1 when starting to move and goes down to 0 when player stops moving
    private double movementSmoothingTime = 0.07;
    // Time of smoothing the player movement in seconds
    private double movementRotationModifier = 0;
    // Horizontal rotation of camera to handle left, right and backwards movement with A, S and D, ie when moving left this is set to 0.5
    private double lastAnimationTime = 0;
    // Holds the time in nanoseconds of when the previous animation timer was played
    private double sprintSmoothing = 0;
    // Same as movementSmoothingTime but used to ease the transition when starting and stopping sprinting
    private double sprintSmoothingTime = 0.07;
    // Time of smoothing the start and stop of sprinting
    private final double baseFOV = 70;
    // Default camera field of view in degrees
    private double currentFOV = baseFOV;
    // Current field of view of the camera that is changed when sprinting
    private double fovSmoothingTime = 0.5;
    // Time of smoothing of increasing and decreasing the camera FOV when starting and ending sprint in seconds
    private double fovModifier = 12.5;
    // Value by how much will the FOV be changed in percent
    private final double baseCameraHeight = 1.8;
    // Default height of camera above ground in meters
    private double currentCameraHeight = baseCameraHeight;
    // Current camera height, that is modified when crouching
    private double jumpingAnimationStartTime = 0;
    // Time of when the jumping animation started in nanoseconds
    private double lastJumpHeight = 0;
    // Previous height of camera mid jumping animation that's needed to calculate how much camera needs to be moved (used in line 266)
    private boolean windowInFocus = true;
    // Flag used to disable camera rotation and show mouse cursor again if user leaves the application
    private double sneakingCameraHeight = 80;
    // Percentage by which the base height will be multiplied when sneaking
    private double sneakAnimationTime = 0.15;
    // How long will the sneaking camera shift take in seconds
    private double sprintSpeedMultiplier = 70;
    // How much faster will the movement be when sprinting in percent (ie if set to 25% the speed will be [movementSpeed * 1.25])
    private double sneakSpeedMultiplier = 50;
    // How much slower will the movement be when sprinting in percent (ie if set to 75% the speed will be [movementSpeed * 0.75])
    private double jumpAnimationTime = 0.6;
    // Time it takes to land after starting jumping in seconds


    private void calculateMovementRotationModifier(){ // Calculate the movement modifier used in movement calculations based on the keyboard inputs
        boolean haltZMovement = (movingForward && movingBackward);

        if(movingForward && !haltZMovement){
            if(movingLeft && !movingRight) movementRotationModifier = PI * 0.75; // Moving forward and left
            else if(movingRight && !movingLeft) movementRotationModifier = PI * -0.75; // Moving forward and right
            else movementRotationModifier = PI * -1;  // Moving only forward
        }
        else if(movingBackward && !haltZMovement){
            if(movingLeft && !movingRight) movementRotationModifier = PI * 0.25; // Moving backward and left
            else if(movingRight && !movingLeft) movementRotationModifier = PI * -0.25; // Moving backward and right
            else movementRotationModifier = 0; // Moving only backward
        }
        else if(movingLeft && !movingRight){
            movementRotationModifier = PI * 0.5; // Moving only left
        }
        else if(movingRight && !movingLeft){
            movementRotationModifier = PI * -0.5; // Moving only right
        }
    }

    @Override
    public void start(Stage primaryStage) {

        // Inverting the time values, so they represent a decimal value of second
        movementSmoothingTime = 1 / movementSmoothingTime;
        sprintSmoothingTime = 1 / sprintSmoothingTime;
        fovSmoothingTime = 1 / fovSmoothingTime;
        sneakAnimationTime = 1 / sneakAnimationTime;
        jumpAnimationTime = 1 / jumpAnimationTime;

        // Changing the percentage values to decimal
        fovModifier = (fovModifier / 100) + 1;
        sneakingCameraHeight /= 100;
        sprintSpeedMultiplier /= 100;
        sneakSpeedMultiplier /= 100;

        Group root = new Group();

        // Set window dimensions
        Scene scene = new Scene(root, 1920, 1080, true, SceneAntialiasing.BALANCED);

        // Fill background (sky) with blue color
        scene.setFill(Color.SKYBLUE);

        // Add perspective camera
        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.getTransforms().add(new Translate(0, baseCameraHeight, 0));
        camera.setFieldOfView(baseFOV);
        scene.setCamera(camera);

        // Listener that checks if window is in focus to handle minimizing of alt-tabing for example
        primaryStage.focusedProperty().addListener((obs, oldVal, newVal) -> {
            windowInFocus = newVal;
            if(newVal){
                scene.setCursor(Cursor.NONE);
                try {
                    Robot robot = new Robot();
                    robot.mouseMove(xCenterPosition, yCenterPosition);
                } catch (AWTException e) {
                    e.printStackTrace();
                }
            }
            else{
                scene.setCursor(Cursor.DEFAULT);
            }
        });

        // Create shapes
        Box box1 = new Box(2, 2, 2);
        PhongMaterial boxMaterial1 = new PhongMaterial();
        boxMaterial1.setDiffuseColor(Color.DARKCYAN);
        box1.setMaterial(boxMaterial1);
        box1.getTransforms().add(new Translate(0, 1, 15));

        Sphere sphere2 = new Sphere(1.5);
        PhongMaterial sphereMaterial2 = new PhongMaterial();
        sphereMaterial2.setDiffuseColor(Color.DARKRED);
        sphere2.setMaterial(sphereMaterial2);
        sphere2.getTransforms().add(new Translate(15, 1.5, 0));

        Sphere sphere3 = new Sphere(1.5);
        PhongMaterial sphereMaterial3 = new PhongMaterial();
        sphereMaterial3.setDiffuseColor(Color.YELLOW);
        sphere3.setMaterial(sphereMaterial3);
        sphere3.getTransforms().add(new Translate(0, 1.5, -15));

        Sphere sphere4 = new Sphere(1.5);
        PhongMaterial sphereMaterial4 = new PhongMaterial();
        sphereMaterial4.setDiffuseColor(Color.WHITE);
        sphere4.setMaterial(sphereMaterial4);
        sphere4.getTransforms().add(new Translate(-15, 1.5, 0));

        // Add shapes to the scene
        root.getChildren().addAll(box1,sphere2,sphere3,sphere4);

        // Create a light source
        PointLight light = new PointLight(Color.rgb(255, 255, 255));
        light.setTranslateX(10);
        light.setTranslateY(20);
        light.setTranslateZ(-15);

        // Create a floor
        Box floor = new Box(300, 0, 300);
        PhongMaterial floorMaterial = new PhongMaterial();
        floorMaterial.setDiffuseColor(Color.GREEN);
        floor.setMaterial(floorMaterial);

        // Handle keyboard events for starting the camera movement
        scene.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            switch (event.getCode()) {
                case W -> movingForward = true;
                case S -> movingBackward = true;
                case A -> movingLeft = true;
                case D -> movingRight = true;
                case SPACE -> isJumping = true;
            }

            isShiftDown = event.isShiftDown();
            isControlDown = event.isControlDown();

            calculateMovementRotationModifier();
        });

        // Handle keyboard events for stopping the camera movement
        scene.addEventHandler(KeyEvent.KEY_RELEASED, event -> {
            switch (event.getCode()) {
                case W -> movingForward = false;
                case S -> movingBackward = false;
                case A -> movingLeft = false;
                case D -> movingRight = false;
            }

            isShiftDown = event.isShiftDown();
            isControlDown = event.isControlDown();

            calculateMovementRotationModifier();
        });

        // Add the elements to the root
        root.getChildren().addAll(light, floor);

        // Add the camera rotations to the camera
        camera.getTransforms().addAll(rotationY, rotationX);

        // Add title, set displayed scene
        primaryStage.setTitle("JavaFX 3D test");
        primaryStage.setScene(scene);
        primaryStage.show();

        // Timer that handles the camera rotation
        AnimationTimer mouseTimer = new AnimationTimer(){
            @Override
            public void handle(long now){
                // Get current mouse position
                java.awt.Point mouseLocation = MouseInfo.getPointerInfo().getLocation();

                // Get current center position of the window, checking constantly to handle user moving the window around
                xCenterPosition = ((int) (scene.getWindow().getX() + scene.getWindow().getWidth() / 2));
                yCenterPosition = ((int) (scene.getWindow().getY() + scene.getWindow().getHeight() / 2));

                // Execute only if window is in focus and the mouse cursor isn't centered
                if((mouseLocation.x != xCenterPosition || mouseLocation.y != yCenterPosition) && windowInFocus){
                    // Calculate mouse movement
                    double deltaX = (mouseLocation.x - xCenterPosition) * mouseSensitivity;
                    double deltaY = (mouseLocation.y - yCenterPosition) * mouseSensitivity;

                    double rotateYAngle = rotationY.getAngle() - deltaX;
                    // Limit horizontal rotation values to <-180;180>
                    if (rotateYAngle >= 180) rotateYAngle -= 360;
                    else if (rotateYAngle <= -180) rotateYAngle += 360;
                    if (deltaX != 0) rotationY.setAngle(rotateYAngle);

                    double rotateXAngle = rotationX.getAngle() + deltaY;
                    // Limit vertical rotation to <-90;90>
                    rotateXAngle = Math.min(90, Math.max(-90, rotateXAngle));
                    if (deltaY != 0) rotationX.setAngle(rotateXAngle);

                    // Convert inverted horizontal rotation in radians
                    rotation = (rotateYAngle * PI * -1) / 180;

                    // Move back mouse cursor to the center of the window
                    try {
                        Robot robot = new Robot();
                        robot.mouseMove(xCenterPosition, yCenterPosition);
                    } catch (AWTException e) {
                        e.printStackTrace();
                    }
                }
            }
        };

        //Create an animation to continuously update the camera movement
        AnimationTimer movementTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                // Calculate time passed between now and last time the timer ran
                double timePassed = 0;
                if (lastAnimationTime != 0) {
                    timePassed = (now - lastAnimationTime) / 1000000000;
                }
                lastAnimationTime = now;

                // Calculate movement smoothing
                if(movingForward || movingBackward || movingLeft || movingRight){
                    if(movementSmoothing < 1) movementSmoothing = Math.min(1, movementSmoothing + (movementSmoothingTime * timePassed));
                }
                else{
                    if(movementSmoothing > 0) movementSmoothing = Math.max(0, movementSmoothing - (movementSmoothingTime * timePassed));
                }

                // Calculate sprint animation smoothing
                if(isShiftDown && movingForward && !movingBackward){ if(sprintSmoothing < 1) sprintSmoothing = Math.min(1, sprintSmoothing + (sprintSmoothingTime * timePassed)); }
                else{ if(sprintSmoothing > 0) sprintSmoothing = Math.max(0, sprintSmoothing - (sprintSmoothingTime * timePassed)); }

                // Calculate sprint FOV shift smoothing
                if(currentFOV < (baseFOV * fovModifier) && (isShiftDown && movingForward && !movingBackward && !isControlDown)){
                    currentFOV = Math.min(baseFOV * fovModifier, currentFOV + (baseFOV  * fovSmoothingTime * timePassed));
                    camera.setFieldOfView(currentFOV);
                }
                else if(!isShiftDown && currentFOV > baseFOV || !movingForward || movingBackward || isControlDown){
                    currentFOV = Math.max(baseFOV, currentFOV - (baseFOV * fovSmoothingTime * timePassed));
                    camera.setFieldOfView(currentFOV);
                }

                // Calculate sneak camera movement
                if(isControlDown){
                    if(currentCameraHeight > baseCameraHeight * sneakingCameraHeight) currentCameraHeight = Math.max(baseCameraHeight * sneakingCameraHeight, currentCameraHeight - (timePassed * sneakAnimationTime));
                    camera.setTranslateY(currentCameraHeight);
                }
                else{
                    if(currentCameraHeight < baseCameraHeight) currentCameraHeight = Math.min(baseCameraHeight, currentCameraHeight + (timePassed * sneakAnimationTime));
                    camera.setTranslateY(currentCameraHeight);
                }

                // Handle jumping animation
                if(isJumping){
                    double currentJumpHeight;
                    double jumpTranslateDistance;
                    if(jumpingAnimationStartTime == 0) {
                        lastJumpHeight = 0;
                        jumpingAnimationStartTime = now;
                    }
                    else if (((now - jumpingAnimationStartTime) * PI) / 1000000000 >= jumpAnimationTime){
                        isJumping = false;
                        jumpingAnimationStartTime = 0;
                        lastJumpHeight = 0;
                        double xMovement = camera.getTransforms().get(0).getTx();
                        double zMovement = camera.getTransforms().get(0).getTz();
                        camera.getTransforms().set(0, new Translate(xMovement, baseCameraHeight, zMovement));
                    }
                    else{
                        currentJumpHeight = Math.pow(Math.abs(Math.sin((((now - jumpingAnimationStartTime) * jumpAnimationTime) / 1000000000) * PI)), 0.95);
                        jumpTranslateDistance = currentJumpHeight - lastJumpHeight;
                        lastJumpHeight = currentJumpHeight;
                        double xMovement = camera.getTransforms().get(0).getTx();
                        double yMovement = camera.getTransforms().get(0).getTy() + jumpTranslateDistance;
                        double zMovement = camera.getTransforms().get(0).getTz();
                        camera.getTransforms().set(0, new Translate(xMovement, yMovement, zMovement));
                    }
                }

                // Handle movement
                if(movementSmoothing > 0){
                    double sumRotation = ((rotation + movementRotationModifier + PI) * -1) % (PI * 2);

                    double xMovement = camera.getTransforms().get(0).getTx() + ((movementSpeed * (isShiftDown && !isControlDown && movingForward && !movingBackward?  1 + (sprintSpeedMultiplier * sprintSmoothing) : 1) * (isControlDown? sneakSpeedMultiplier : 1) * movementSmoothing) * Math.sin(sumRotation) * timePassed);
                    double yMovement = camera.getTransforms().get(0).getTy();
                    double zMovement = camera.getTransforms().get(0).getTz() + ((movementSpeed * (isShiftDown && !isControlDown && movingForward && !movingBackward? 1 + (sprintSpeedMultiplier * sprintSmoothing) : 1) * (isControlDown? sneakSpeedMultiplier : 1) * movementSmoothing) * Math.cos(sumRotation) * timePassed);

                    camera.getTransforms().set(0, new Translate(xMovement, yMovement, zMovement));
                    floor.setTranslateX(xMovement);
                    floor.setTranslateZ(zMovement);
                }
            }
        };

        // Set the window center position at start
        xCenterPosition = ((int) (scene.getWindow().getX() + scene.getWindow().getWidth() / 2));
        yCenterPosition = ((int) (scene.getWindow().getY() + scene.getWindow().getHeight() / 2));

        // Rotate camera upside down (because without that moving in Y axis was inverted, adding negative translation moved stuff up and vice versa)
        camera.getTransforms().add(new Rotate(180, 0, 0));

        // Starting the timers
        movementTimer.start();
        mouseTimer.start();

        // Move the mouse to center of the screen
        try {
            Robot robot = new Robot();
            robot.mouseMove(xCenterPosition, yCenterPosition);
        } catch (AWTException e) {
            e.printStackTrace();
        }

    }

    public static void main(String[] args) {
        launch();
    }
}