package com.ziehro.starcrossed;
import static android.content.ContentValues.TAG;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private LinearLayout canvasLayout;
    private DatePickerDialog datePickerDialog;
    private TextView luckyNumbersTextView;
    private TextView dateTV;

    private Button drawButton;
    private SolarSystemView solarSystemView;

    private float[] planetOrbitRadii = {100, 200, 300, 400, 500, 600, 700, 800}; // Radii of planet orbits
    private String[] planetNames = {"mercury", "venus", "earth", "mars", "jupiter", "saturn", "uranus", "neptune"};
    private Map<String, String> planetSymbols = new HashMap<String, String>() {{
        put("mercury", "\u263F"); // ☿
        put("venus", "\u2640");   // ♀
        put("earth", "\u2295");   // ⊕
        put("mars", "\u2642");    // ♂
        put("jupiter", "\u2643"); // ♃
        put("saturn", "\u2644");  // ♄
        put("uranus", "\u2645");  // ♅
        put("neptune", "\u2646"); // ♆
    }};

    private float centerX;
    private float centerY;
    Button emailButton;
    private ScaleGestureDetector scaleGestureDetector;

    int[] colors = new int[] {
            Color.parseColor("#ff0000"),    // January - Red
            Color.parseColor("#ff7f00"),    // February - Orange
            Color.parseColor("#ffff00"),    // March - Yellow
            Color.parseColor("#00ff00"),    // April - Green
            Color.parseColor("#00ffff"),    // May - Cyan
            Color.parseColor("#007fff"),    // June - Light Blue
            Color.parseColor("#0000ff"),    // July - Blue
            Color.parseColor("#7f00ff"),    // August - Violet
            Color.parseColor("#ff00ff"),    // September - Magenta
            Color.parseColor("#ff007f"),    // October - Rose
            Color.parseColor("#7f7f7f"),    // November - Grey
            Color.parseColor("#ffffff")     // December - White
    };



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        canvasLayout = findViewById(R.id.canvas_layout);
        drawButton = findViewById(R.id.draw_button);

        luckyNumbersTextView = findViewById(R.id.luckyNumbersTextView);
        dateTV = findViewById(R.id.dateTV2);



        // Initialize a calendar instance
        final Calendar calendar = Calendar.getInstance();

        emailButton = findViewById(R.id.email_button);

        emailButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Create AlertDialog for title input
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Enter a title");

                // Set up the input
                final EditText input = new EditText(MainActivity.this);
                builder.setView(input);

                // Set up the buttons
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String title = input.getText().toString();

                        // Add title to the solar system view
                        SolarSystemView solarSystemView = (SolarSystemView) canvasLayout.getChildAt(0);  // Assuming SolarSystemView is the first child of canvasLayout
                        solarSystemView.setTitle(title);
                        solarSystemView.invalidate();  // Redraw the view with the new title

                        try {
                            File imageFile = createImageFile(canvasLayout);

                            // Create email intent
                            Intent emailIntent = new Intent(Intent.ACTION_SEND);
                            emailIntent.setType("image/jpg");
                            emailIntent.putExtra(Intent.EXTRA_SUBJECT, "My Solar System Image");
                            emailIntent.putExtra(Intent.EXTRA_TEXT, "See attached image.");
                            emailIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                            // Convert file to content URI (FileProvider is recommended way after Android N)
                            Uri contentUri = FileProvider.getUriForFile(MainActivity.this, getApplicationContext().getPackageName() + ".provider", imageFile);
                            emailIntent.putExtra(Intent.EXTRA_STREAM, contentUri);

                            // Start email intent
                            if (emailIntent.resolveActivity(getPackageManager()) != null) {
                                startActivity(Intent.createChooser(emailIntent, "Send email..."));
                            }
                        } catch (IOException e) {
                            Log.e("EmailButton", "Error creating image file: " + e.getMessage());
                        }
                    }
                });
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

                builder.show();
            }
        });


        Button clearButton = findViewById(R.id.clearButton);
        clearButton.setText("Clear Canvas");
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // When the button is clicked, remove all planet positions and clear the canvas
                allPlanetPositions.clear();
                canvasLayout.removeAllViews();
                canvasLayout.invalidate();
            }
        });



        // Setup DatePickerDialog
        datePickerDialog = new DatePickerDialog(
                MainActivity.this,
                new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                        // convert the DatePicker date into the format expected by the ephemeris data
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MMM-dd", Locale.US);
                        Calendar c = Calendar.getInstance();
                        c.set(year, month, dayOfMonth);
                        String birthdate = sdf.format(c.getTime()) + " 00:00";

                        drawSolarSystem(birthdate);
                        float[] planetPositions = calculatePlanetPositions(birthdate);
                        List<Integer> luckyNumbers = generateLuckyNumbers(planetPositions);

                        Collections.sort(luckyNumbers);
                        luckyNumbersTextView.setText(luckyNumbers.toString());
                        dateTV.setText(sdf.format(c.getTime()));


                    }
                },
                calendar.get(Calendar.YEAR), // Initial year selection
                calendar.get(Calendar.MONTH), // Initial month selection
                calendar.get(Calendar.DAY_OF_MONTH) // Inital day selection
        );



        drawButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                datePickerDialog.show();
            }
        });
    }


    private List<float[]> allPlanetPositions = new ArrayList<>();

    private void drawSolarSystem(String birthdate) {
        // Create an asynchronous task to handle the network request
        AsyncTask<String, Void, float[]> task = new AsyncTask<String, Void, float[]>() {
            @Override
            protected float[] doInBackground(String... params) {
                String birthdate = params[0];
                return calculatePlanetPositions(birthdate);
            }

            @Override
            protected void onPostExecute(float[] planetPositions) {
                allPlanetPositions.add(planetPositions);

                canvasLayout.removeAllViews();  // We still need this line to clear old views

                // Create a new SolarSystemView with all planet positions
                SolarSystemView solarSystemView = new SolarSystemView(MainActivity.this, allPlanetPositions);
                canvasLayout.addView(solarSystemView);
                solarSystemView.invalidate();

                // Initialize scaleGestureDetector after SolarSystemView has been created
                scaleGestureDetector = new ScaleGestureDetector(MainActivity.this, new ScaleListener(solarSystemView));
            }
        };

        task.execute(birthdate);
    }




    private class SolarSystemView extends View {
        private Paint orbitPaint;
        private Paint planetPaint;
        private Paint linePaint;
        private Paint textPaint;
        private Paint hullPaint;
        private Paint starlinePaint;
        private float[] planetPositions; // Store planet positions here
        private List<float[]> allPlanetPositions = new ArrayList<>(); // List of planet positions for each date

        private float orbitSpacing; // Spacing between each orbit
        private float mScaleFactor = 1.0f;
        private float lastTouchX;
        private float lastTouchY;
        private float translateX = 0.0f;
        private float translateY = 0.0f;
        private boolean isPanning = false;
        private String title = "";

        public void setTitle(String title) {
            this.title = title;
        }


        public SolarSystemView(Context context, List<float[]> allPlanetPositions) {
            super(context);
            this.allPlanetPositions = allPlanetPositions; // Assign planet positions
            //this.mScaleFactor = scaleFactor; // Assign scaling factor

            // Initialize paints and other properties
            orbitPaint = new Paint();
            orbitPaint.setColor(Color.GRAY);
            orbitPaint.setStyle(Paint.Style.STROKE);

            planetPaint = new Paint();
            planetPaint.setColor(Color.BLUE);
            planetPaint.setStyle(Paint.Style.FILL);

            linePaint = new Paint();
            linePaint.setColor(Color.YELLOW);
            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeWidth(2);

            hullPaint = new Paint();
            hullPaint.setColor(Color.GREEN);
            hullPaint.setStyle(Paint.Style.STROKE);
            hullPaint.setStrokeWidth(2);

            starlinePaint = new Paint();
            starlinePaint.setColor(Color.CYAN);
            starlinePaint.setStyle(Paint.Style.STROKE);
            starlinePaint.setStrokeWidth(2);

            textPaint = new Paint();
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(80);
            textPaint.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            scaleGestureDetector.onTouchEvent(event);

            // Handle panning
            float touchX = event.getX();
            float touchY = event.getY();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    lastTouchX = touchX;
                    lastTouchY = touchY;
                    isPanning = true;
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (isPanning) {
                        float dx = touchX - lastTouchX;
                        float dy = touchY - lastTouchY;
                        translateX += dx;
                        translateY += dy;
                        invalidate();
                    }
                    lastTouchX = touchX;
                    lastTouchY = touchY;
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isPanning = false;
                    break;
            }

            return true;
        }
        public void setPlanetPositions(float[] planetPositions) {
            this.planetPositions = planetPositions;
            invalidate();
        }

        public void scale(float scaleFactor) {
            mScaleFactor *= scaleFactor;
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);

            // Calculate the maximum orbit radius and spacing
            float padding = 40; // Add some padding around the edges
            float availableRadius = Math.min(w, h) / 2f - padding;
            int numberOfOrbits = planetOrbitRadii.length;
            orbitSpacing = availableRadius / (numberOfOrbits + 1);
            // Maximum radius that fits on the screen
            float maxOrbitRadius = orbitSpacing * numberOfOrbits;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.save();
            canvas.scale(mScaleFactor, mScaleFactor);
            canvas.translate(translateX, translateY);

            centerX = canvas.getWidth() / 2f;
            centerY = canvas.getHeight() / 2f;

            Paint starPaint = new Paint();
            starPaint.setColor(Color.WHITE);

            Random rand = new Random();
            canvas.drawText(title, canvas.getWidth() / 2, canvas.getHeight() - 50, textPaint);


            int numStars = 200;  // Change this to the number of stars you want
            float starRadius = 2.0f;  // Radius of each star. Adjust as desired.
            for (int i = 0; i < numStars; i++) {
                float x = rand.nextFloat() * getWidth();
                float y = rand.nextFloat() * getHeight();
                canvas.drawCircle(x, y, starRadius, starPaint);
            }

            // Draw planet orbits
            for (int i = 0; i < planetOrbitRadii.length; i++) {
                float radius = orbitSpacing * (i + 1);
                canvas.drawCircle(centerX, centerY, radius, orbitPaint);
            }

            // Collect all planet points
            for (float[] planetPositions : allPlanetPositions) { // Iterate over each set of planet positions
                // Collect all planet points
                List<PointF> planetPoints = new ArrayList<>();
                int randomNum = rand.nextInt(12);
                starlinePaint.setColor(colors[randomNum]);

                // Draw planets on orbits
                for (int i = 0; i < planetPositions.length; i++) {
                    float angle = (float) Math.toRadians(planetPositions[i]); // Convert angle to radians
                    float radius = orbitSpacing * (i + 1);

                    float x = centerX + radius * (float) Math.cos(angle);
                    float y = centerY + radius * (float) Math.sin(angle);

                    // Collect this planet point
                    planetPoints.add(new PointF(x, y));

                    canvas.drawCircle(x, y, 10, planetPaint);
                    canvas.drawText(planetSymbols.get(planetNames[i]), x, y - 40, textPaint);


                    canvas.drawLine(centerX, centerY, x, y, starlinePaint);


                    // Connect the planets with lines
                    if (i < planetPositions.length - 1) {
                        float nextAngle = (float) Math.toRadians(planetPositions[i + 1]); // Convert angle to radians
                        float nextRadius = orbitSpacing * (i + 2);

                        float nextX = centerX + nextRadius * (float) Math.cos(nextAngle);
                        float nextY = centerY + nextRadius * (float) Math.sin(nextAngle);

                        //canvas.drawLine(x, y, nextX, nextY, linePaint);
                    }
                }

                // Calculate the convex hull points
                List<PointF> hullPoints = ((MainActivity) getContext()).calculateConvexHull(planetPoints);

                // Draw the convex hull
           /* PointF previousPoint = hullPoints.get(hullPoints.size() - 1);  // Start with the last point
            for (PointF point : hullPoints) {
                canvas.drawLine(previousPoint.x, previousPoint.y, point.x, point.y, hullPaint);
                previousPoint = point;
            }*/

            }
            canvas.restore();
        }


    }

    private float[] calculatePlanetPositions(String birthdate) {
        float[] planetPositions = new float[planetNames.length];

        for(int i=0; i<planetNames.length; i++) {
            BufferedReader reader = null;
            try {
                // Open the ephemeris data file for the current planet
                reader = new BufferedReader(new InputStreamReader(getAssets().open(planetNames[i] + ".txt")));

                // Read the data
                String line;
                boolean dataSection = false;  // flag to track when we've reached the data section

                while ((line = reader.readLine()) != null) {
                    if(line.trim().equals("$$SOE")) {
                        dataSection = true;  // we've reached the data section
                        continue;
                    }

                    if(line.trim().equals("$$EOE")) {
                        dataSection = false;  // we've left the data section
                        continue;
                    }

                    if(dataSection) {
                        // Parse the data
                        // The format seems to be: date, time, _, _, right ascension, declination, _, _, _, _, longitude, latitude
                        String[] elements = line.split(",");
                        if (elements.length > 10) {
                            String fileDate = elements[0].trim();
                            // Use only the data for the requested birthdate
                            if(fileDate.equals(birthdate)) {
                                float longitude = Float.parseFloat(elements[9].trim());
                                planetPositions[i] = longitude;
                                Log.d(TAG, "calculatePlanetPositions: " +longitude);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                Log.e("calculatePlanetPositions", "Error reading ephemeris data: " + e.getMessage());
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        Log.e("calculatePlanetPositions", "Error closing reader: " + e.getMessage());
                    }
                }
            }
        }

        return planetPositions;
    }

    private File createImageFilePNG(View view) throws IOException {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        view.draw(canvas);

        // Save bitmap to cache directory
        File cacheDir = getExternalCacheDir();
        if(cacheDir == null) throw new IOException("Unable to get cache directory");
        File imageFile = new File(cacheDir, "solar_system.png");
        FileOutputStream fos = new FileOutputStream(imageFile);
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
        fos.close();

        return imageFile;
    }

    private File createImageFile(View view) throws IOException {
        // Create a bitmap of the view
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        view.draw(canvas);

        // Save bitmap to cache directory
        File cacheDir = getExternalCacheDir();
        if(cacheDir == null) throw new IOException("Unable to get cache directory");

        // Change the filename extension to .jpg
        File imageFile = new File(cacheDir, "solar_system.jpg");

        FileOutputStream fos = new FileOutputStream(imageFile);

        // Change the compress format to JPEG
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);

        fos.close();

        return imageFile;
    }


    public List<PointF> calculateConvexHull(List<PointF> points) {
        // We need at least three points for a polygon
        if (points.size() < 3) return points;

        // Find the leftmost point
        int l = 0;
        for (int i = 1; i < points.size(); i++) {
            if (points.get(i).x < points.get(l).x) {
                l = i;
            }
        }

        // Initialize result
        List<PointF> hull = new ArrayList<>();

        // Start from leftmost point, keep moving counterclockwise
        int p = l, q;
        do {
            // Add current point to result
            hull.add(points.get(p));

            // Search for a point 'q' such that orientation(p, x, q) is counterclockwise for all points 'x'.
            q = (p + 1) % points.size();

            for (int i = 0; i < points.size(); i++) {
                if (orientation(points.get(p), points.get(i), points.get(q)) == 2) {
                    q = i;
                }
            }

            // Now q is the most counterclockwise with respect to p. Set p as q for next iteration.
            p = q;

        } while (p != l);  // While we don't come to first point

        return hull;
    }

    // A utility function to find next orientation. The function returns following values
// 0 --> p, q and r are colinear
// 1 --> Clockwise
// 2 --> Counterclockwise
    public int orientation(PointF p, PointF q, PointF r) {
        float val = (q.y - p.y) * (r.x - q.x) -
                (q.x - p.x) * (r.y - q.y);

        if (val == 0) return 0;  // Colinear
        return (val > 0)? 1: 2;  // Clock or counterclock
    }

    public List<Integer> generateLuckyNumbers(float[] planetPositions) {
        Set<Integer> luckyNumbers = new HashSet<>();  // Use a Set to avoid duplicates

        for (float position : planetPositions) {
            // Normalize the position (which could be a negative number or a large number) to a value between 1 and 49
            int number = Math.abs(((int) position) % 49) + 1;

            // Add the number to the set of lucky numbers
            luckyNumbers.add(number);

            // If we have 7 unique lucky numbers, then we're done
            if (luckyNumbers.size() == 7) break;
        }

        // If there are fewer than 7 planets, or if some planets resulted in the same lucky number, you might
        // not have 7 lucky numbers at this point. You should decide how to handle this case.

        return new ArrayList<>(luckyNumbers);
    }
    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        private SolarSystemView view;

        ScaleListener(SolarSystemView view) {
            this.view = view;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scaleFactor = detector.getScaleFactor();
            view.scale(scaleFactor);
            return true;
        }
    }

    public int getMonth(String birthdate) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        try {
            Date date = format.parse(birthdate);
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(date);
            return calendar.get(Calendar.MONTH); // Note that January is 0
        } catch (ParseException e) {
            e.printStackTrace();
            return 0;
        }
    }


}

