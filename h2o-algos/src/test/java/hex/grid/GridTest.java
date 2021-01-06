package hex.grid;

import hex.Model;
import hex.faulttolerance.Recovery;
import hex.genmodel.utils.DistributionFamily;
import hex.glm.GLMModel;
import hex.tree.gbm.GBMModel;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import water.*;
import water.fvec.Frame;
import water.test.dummy.DummyModelParameters;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class GridTest extends TestUtil {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Before
  public void setUp() {
    TestUtil.stall_till_cloudsize(1);
  }

  @Test
  public void testParallelModelTimeConstraint() {
    try {
      Scope.enter();

      final Frame trainingFrame = parseTestFile("./smalldata/testng/airlines_train.csv");
      Scope.track(trainingFrame);

      final Integer[] ntreesArr = new Integer[]{5, 50, 7, 8, 9, 10, 500};
      final Integer[] maxDepthArr = new Integer[]{2, 3, 4};
      HashMap<String, Object[]> hyperParms = new HashMap<String, Object[]>() {{
        put("_distribution", new DistributionFamily[]{DistributionFamily.multinomial});
        put("_ntrees", ntreesArr);
        put("_max_depth", maxDepthArr);
      }};

      GBMModel.GBMParameters params = new GBMModel.GBMParameters();
      params._train = trainingFrame._key;
      params._response_column = "IsDepDelayed";
      params._seed = 42;

      HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria searchCriteria = new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria();
      searchCriteria.set_max_runtime_secs(1d);

      Job<Grid> gridSearch = GridSearch.startGridSearch(
          null, params, hyperParms,
            new GridSearch.SimpleParametersBuilderFactory(),
            searchCriteria, 2
      );

      Scope.track_generic(gridSearch);
      final Grid grid = gridSearch.get();
      Scope.track_generic(grid);

     assertNotEquals(ntreesArr.length * maxDepthArr.length, grid.getModelCount());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testParallelUserStopRequest() {
    try {
      Scope.enter();

      final Frame trainingFrame = parseTestFile("./smalldata/testng/airlines_train.csv");
      Scope.track(trainingFrame);

      final Integer[] ntreesArr = new Integer[]{5, 50, 7, 8, 9, 10, 500};
      final Integer[] maxDepthArr = new Integer[]{2, 3, 4, 50};
      HashMap<String, Object[]> hyperParms = new HashMap<String, Object[]>() {{
        put("_distribution", new DistributionFamily[]{DistributionFamily.multinomial});
        put("_ntrees", ntreesArr);
        put("_max_depth", maxDepthArr);
      }};

      GBMModel.GBMParameters params = new GBMModel.GBMParameters();
      params._train = trainingFrame._key;
      params._response_column = "IsDepDelayed";
      params._seed = 42;

      Job<Grid> gridSearch = GridSearch.startGridSearch(           
          null, params, hyperParms,
          new GridSearch.SimpleParametersBuilderFactory(),
          new HyperSpaceSearchCriteria.CartesianSearchCriteria(), 
          2
      );
      Scope.track_generic(gridSearch);
      gridSearch.stop();
      final Grid grid = gridSearch.get();
      Scope.track_generic(grid);
      
      for(Model m : grid.getModels()){
        Scope.track_generic(m);
      }
    
      assertNotEquals(ntreesArr.length * maxDepthArr.length, grid.getModelCount());

    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testParallelGridSearch() {
    try {
      Scope.enter();

      final Frame trainingFrame = parseTestFile("./smalldata/testng/airlines_train.csv");
      Scope.track(trainingFrame);

      final Integer[] ntreesArr = new Integer[]{5, 50, 7, 8, 9, 10};
      final Integer[] maxDepthArr = new Integer[]{2, 3, 4};
      HashMap<String, Object[]> hyperParms = new HashMap<String, Object[]>() {{
        put("_distribution", new DistributionFamily[]{DistributionFamily.multinomial});
        put("_ntrees", ntreesArr);
        put("_max_depth", maxDepthArr);
      }};

      GBMModel.GBMParameters params = new GBMModel.GBMParameters();
      params._train = trainingFrame._key;
      params._response_column = "IsDepDelayed";
      params._seed = 42;

      Job<Grid> gs = GridSearch.startGridSearch(null, params, hyperParms, 5);
      Scope.track_generic(gs);
      final Grid grid = gs.get();
      Scope.track_generic(grid);

      assertEquals(ntreesArr.length * maxDepthArr.length, grid.getModelCount());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testAdaptiveParallelGridSearch() {
    try {
      Scope.enter();

      final Frame trainingFrame = parseTestFile("./smalldata/testng/airlines_train.csv");
      Scope.track(trainingFrame);

      final Integer[] ntreesArr = new Integer[]{5, 50, 7, 8, 9, 10};
      final Integer[] maxDepthArr = new Integer[]{2, 3, 4};
      HashMap<String, Object[]> hyperParms = new HashMap<String, Object[]>() {{
        put("_distribution", new DistributionFamily[]{DistributionFamily.multinomial});
        put("_ntrees", ntreesArr);
        put("_max_depth", maxDepthArr);
      }};

      GBMModel.GBMParameters params = new GBMModel.GBMParameters();
      params._train = trainingFrame._key;
      params._response_column = "IsDepDelayed";
      params._seed = 42;

      final Job<Grid> gs = GridSearch.startGridSearch(null, params, hyperParms,
              GridSearch.getAdaptiveParallelism());
      Scope.track_generic(gs);
      final Grid secondGrid = gs.get();
      Scope.track_generic(secondGrid);
      assertEquals(ntreesArr.length * maxDepthArr.length, secondGrid.getModelCount());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testFaileH2OdParamsCleanup() {
    try {
      Scope.enter();
      final Frame trainingFrame = parseTestFile("smalldata/iris/iris_train.csv");
      Scope.track(trainingFrame);

      // Setup random hyperparameter search space
      HashMap<String, Object[]> hyperParms = new HashMap<String, Object[]>() {{
        put("_distribution", new DistributionFamily[]{DistributionFamily.multinomial});
        put("_ntrees", new Integer[]{5});
        put("_max_depth", new Integer[]{2});
        put("_min_rows", new Integer[]{5000000}); // Invalid hyperparameter, causes model training to fail
        put("_learn_rate", new Double[]{.7});
      }};

      GBMModel.GBMParameters params = new GBMModel.GBMParameters();
      params._train = trainingFrame._key;
      params._response_column = "species";

      Job<Grid> gs = GridSearch.startGridSearch(null, params, hyperParms);
      Scope.track_generic(gs);
      final Grid errGrid = gs.get();
      Scope.track_generic(errGrid);

      assertEquals(0, errGrid.getModelCount());

      final Grid.SearchFailure failures = errGrid.getFailures();
      assertEquals(1, failures.getFailureCount());
      assertEquals(1, failures.getFailedParameters().length);
      assertEquals(1, failures.getFailedRawParameters().length);
      assertEquals(1, failures.getFailureDetails().length);
      assertEquals(1, failures.getFailureStackTraces().length);

      // Check if the error is related to the specified invalid hyperparameter
      final String expectedErr = "Details: ERRR on field: _min_rows: The dataset size is too small to split for min_rows=5000000.0: must have at least 1.0E7 (weighted) rows";
      assertTrue(failures.getFailureStackTraces()[0].contains(expectedErr));

      //Set the parameter to an acceptable value
      hyperParms.put("_min_rows", new Integer[]{10});
      gs = GridSearch.startGridSearch(errGrid._key, params, hyperParms); // It is important to target the previously created grid
      Scope.track_generic(gs);
      final Grid grid = gs.get();
      Scope.track_generic(grid);

      // There should be no errors, one resulting model in the grid with previously supplied parameters
      assertEquals(1, grid.getModelCount());
      assertTrue(grid.getModels()[0] instanceof GBMModel);
      assertEquals(10, ((GBMModel) grid.getModels()[0])._parms._min_rows, 0);

      final Grid.SearchFailure secondRunFailures = grid.getFailures();
      assertEquals(0, secondRunFailures.getFailureCount());
      assertEquals(0, secondRunFailures.getFailedParameters().length);
      assertEquals(0, secondRunFailures.getFailedRawParameters().length);
      assertEquals(0, secondRunFailures.getFailureDetails().length);
      assertEquals(0, secondRunFailures.getFailureStackTraces().length);

    } finally {
      Scope.exit();
    }
  }

  @Test
  public void gridSearchExportCheckpointsDir() throws IOException {
    try {
      Scope.enter();

      final Frame trainingFrame = parseTestFile("smalldata/iris/iris_train.csv");
      Scope.track(trainingFrame);

      HashMap<String, Object[]> hyperParms = new HashMap<String, Object[]>() {{
        put("_distribution", new DistributionFamily[]{DistributionFamily.multinomial});
        put("_ntrees", new Integer[]{5});
        put("_max_depth", new Integer[]{2});
        put("_learn_rate", new Double[]{.7});
      }};

      GBMModel.GBMParameters params = new GBMModel.GBMParameters();
      params._train = trainingFrame._key;
      params._response_column = "species";
      params._export_checkpoints_dir = temporaryFolder.newFolder().getAbsolutePath();

      Job<Grid> gs = GridSearch.startGridSearch(null, params, hyperParms);
      Scope.track_generic(gs);
      final Grid originalGrid = gs.get();
      Scope.track_generic(originalGrid);

      final File serializedGridFile = new File(params._export_checkpoints_dir, originalGrid._key.toString());
      assertTrue(serializedGridFile.exists());
      assertTrue(serializedGridFile.isFile());
      
      final Grid grid = loadGridFromFile(serializedGridFile);
      assertArrayEquals(originalGrid.getModelKeys(), grid.getModelKeys());
      Scope.track_generic(grid);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void gridSearchWithRecoverySuccess() throws IOException, InterruptedException {
    try {
      Scope.enter();

      final Frame trainingFrame = parseTestFile("smalldata/iris/iris_train.csv");
      Scope.track(trainingFrame);

      HashMap<String, Object[]> hyperParms = new HashMap<String, Object[]>() {{
        put("_ntrees", new Integer[]{5, 50, 100, 200});
        put("_max_depth", new Integer[]{2, 4, 6});
      }};

      GBMModel.GBMParameters params = new GBMModel.GBMParameters();
      params._train = trainingFrame._key;
      params._response_column = "species";
      String recoveryDir = temporaryFolder.newFolder().getAbsolutePath();
      Recovery<Grid> recovery = new Recovery<>(recoveryDir);
      Key gridKey = Key.make("gridSearchWithRecovery_GRID");
      Job<Grid> gs = GridSearch.startGridSearch(
          gridKey, params, hyperParms,
          new GridSearch.SimpleParametersBuilderFactory<>(),
          new HyperSpaceSearchCriteria.CartesianSearchCriteria(),
          recovery, GridSearch.SEQUENTIAL_MODEL_BUILDING
      );
      Scope.track_generic(gs);
      Grid gridInProgress = DKV.getGet(gridKey);
      Scope.track_generic(gridInProgress);
      while (gs.isRunning() && gridInProgress.getModelKeys().length == 0) {
        System.out.println("sleeping...");
        Thread.sleep(100);
        gridInProgress = DKV.getGet(gridKey);
      }
      assertTrue(
          "Some files should be in the recovery directory (grid in progress)", 
          new File(recoveryDir).listFiles().length > 0
      );
          
      // wait for grid to finish and check cleanup was done
      gs.get();
      assertEquals(
          "Recovery directory should be empty after successful grid. " +
              Arrays.toString(new File(recoveryDir).list()), 
          new File(recoveryDir).listFiles().length, 0
      );
    } finally {
      Scope.exit();
    }
  }
  
  private void testGridRecovery(Key gridKey, Job gs, Frame train, String recoveryDir) throws IOException, InterruptedException {
    Grid originalGrid = DKV.getGet(gridKey);
    Scope.track_generic(originalGrid);
    while (gs.isRunning() && originalGrid.getModelKeys().length == 0) {
      Thread.sleep(100);
    }
    assertTrue("Some files should be in the recovery directory", new File(recoveryDir).listFiles().length > 0);
    gs.stop();

    // wait for grid to finish and check cleanup was done
    while (gs.isRunning()) {
      Thread.sleep(100);
    }
    assertNotEquals(
        "Recovery directory should not be empty after canceled grid.",
        new File(recoveryDir).listFiles().length, 0
    );
    
    Key<Model>[] originalKeys = originalGrid.getModelKeys();
    originalGrid.remove();
    train.remove();
    assertNull("models should be removed from dkv as well", originalKeys[0].get());

    final File serializedGridFile = new File(recoveryDir, originalGrid._key.toString());
    assertTrue(serializedGridFile.isFile());

    final Grid grid = loadGridFromFile(serializedGridFile);
    DKV.put(grid);
    grid.importModelsBinary(recoveryDir);
    new Recovery<Grid>(recoveryDir).loadReferences(grid);
    assertArrayEquals("models are not reloaded with the grid", originalKeys, grid.getModelKeys());
    assertNotNull("training frame was not reloaded with the grid", train._key.get());
    assertNotNull("models should loaded back into dkv", originalKeys[0].get());
    Scope.track_generic(grid);
  }

  @Test
  @Ignore // GBM leaks keys when canceled
  public void gridSearchWithRecoveryCancelGBM() throws IOException, InterruptedException {
    try {
      Scope.enter();

      final Frame trainingFrame = parseTestFile("smalldata/iris/iris_train.csv");
      Scope.track(trainingFrame);

      HashMap<String, Object[]> hyperParms = new HashMap<String, Object[]>() {{
        put("_ntrees", new Integer[]{5, 50, 100});
        put("_max_depth", new Integer[]{2});
      }};

      GBMModel.GBMParameters params = new GBMModel.GBMParameters();
      params._train = trainingFrame._key;
      params._response_column = "species";
      String recoveryDir = temporaryFolder.newFolder().getAbsolutePath();
      Recovery<Grid> recovery = new Recovery<>(recoveryDir);
      Key gridKey = Key.make("gridSearchWithRecovery_GRID");
      Job<Grid> gs = GridSearch.startGridSearch(
          gridKey, params, hyperParms,
          new GridSearch.SimpleParametersBuilderFactory<>(),
          new HyperSpaceSearchCriteria.CartesianSearchCriteria(),
          recovery, GridSearch.SEQUENTIAL_MODEL_BUILDING
      );
      Scope.track_generic(gs);
      testGridRecovery(gridKey, gs, trainingFrame, recoveryDir);

    } finally {
      Scope.exit();
    }
  }
  
  private Object[] toArrayOfArrays(double[] arr) {
    Object[] res = new Object[arr.length];
    for (int i = 0; i < arr.length; i++) {
      res[i] = new double[] { arr[i] };
    }
    return res;
  }

  @Test
  @Ignore // fails on multi node
  public void gridSearchWithRecoveryCancelGLM() throws IOException, InterruptedException {
    try {
      Scope.enter();

      final Frame trainingFrame = parseTestFile("smalldata/junit/cars_20mpg.csv");
      Scope.track(trainingFrame);
      trainingFrame.remove(0);

      HashMap<String, Object[]> hyperParms = new HashMap<String, Object[]>() {{
        put("_alpha", toArrayOfArrays(new double[]{ 0.01, 0.3, 0.5, 0.7, 0.9}));
        put("_lambda", toArrayOfArrays(new double[]{ 1e-5, 1e-6, 1e-7, 1e-8, 5e-5, 5e-6, 5e-7, 5e-8}));
      }};

      GLMModel.GLMParameters params = new GLMModel.GLMParameters();
      params._train = trainingFrame._key;
      params._response_column = "cylinders";
      String recoveryDir = temporaryFolder.newFolder().getAbsolutePath();
      Recovery<Grid> recovery = new Recovery<>(recoveryDir);
      Key gridKey = Key.make("gridSearchWithRecoveryGlm");
      Job<Grid> gs = GridSearch.startGridSearch(
          gridKey, params, hyperParms,
          new GridSearch.SimpleParametersBuilderFactory<>(),
          new HyperSpaceSearchCriteria.CartesianSearchCriteria(),
          recovery, GridSearch.SEQUENTIAL_MODEL_BUILDING
      );
      Scope.track_generic(gs);
      testGridRecovery(gridKey, gs, trainingFrame, recoveryDir);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void gridSearchManualExport() throws IOException {
    try {
      Scope.enter();

      final Frame trainingFrame = parseTestFile("smalldata/iris/iris_train.csv");
      Scope.track(trainingFrame);

      HashMap<String, Object[]> hyperParms = new HashMap<String, Object[]>() {{
        put("_distribution", new DistributionFamily[]{DistributionFamily.multinomial});
        put("_ntrees", new Integer[]{5});
        put("_max_depth", new Integer[]{2});
        put("_learn_rate", new Double[]{.7});
      }};

      GBMModel.GBMParameters params = new GBMModel.GBMParameters();
      params._train = trainingFrame._key;
      params._response_column = "species";

      final String exportDir = temporaryFolder.newFolder().getAbsolutePath();

      Job<Grid> gs = GridSearch.startGridSearch(null, params, hyperParms, 1);
      Scope.track_generic(gs);
      final Grid originalGrid = gs.get();
      Scope.track_generic(originalGrid);
      
      final String originalGridPath = exportDir + "/" + originalGrid._key.toString();
      originalGrid.exportBinary(originalGridPath);
      assertTrue(Files.exists(Paths.get(originalGridPath)));
      
      originalGrid.exportModelsBinary(exportDir);
      
      for(Model model : originalGrid.getModels()){
        assertTrue(Files.exists(Paths.get(exportDir, model._key.toString())));  
      }
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void gridSearchExportCheckpointsDirParallel() throws IOException {
    try {
      Scope.enter();

      final Frame trainingFrame = parseTestFile("smalldata/iris/iris_train.csv");
      Scope.track(trainingFrame);

      HashMap<String, Object[]> hyperParms = new HashMap<String, Object[]>() {{
        put("_distribution", new DistributionFamily[]{DistributionFamily.multinomial});
        put("_ntrees", new Integer[]{5, 10, 50});
        put("_max_depth", new Integer[]{2,3});
        put("_learn_rate", new Double[]{.7});
      }};

      GBMModel.GBMParameters params = new GBMModel.GBMParameters();
      params._train = trainingFrame._key;
      params._response_column = "species";
      params._export_checkpoints_dir = temporaryFolder.newFolder().getAbsolutePath();

      Job<Grid> gs = GridSearch.startGridSearch(null, params, hyperParms, 2);
      Scope.track_generic(gs);
      final Grid originalGrid = gs.get();
      Scope.track_generic(originalGrid);

      final File serializedGridFile = new File(params._export_checkpoints_dir, originalGrid._key.toString());
      assertTrue(serializedGridFile.exists());
      assertTrue(serializedGridFile.isFile());

      final Grid grid = loadGridFromFile(serializedGridFile);
      assertArrayEquals(originalGrid.getModelKeys(), grid.getModelKeys());
      Scope.track_generic(grid);
    } finally {
      Scope.exit();
    }
  }

  private static Grid loadGridFromFile(final File file) throws IOException {
    try (final FileInputStream fileInputStream = new FileInputStream(file)) {
      final AutoBuffer autoBuffer = new AutoBuffer(fileInputStream);
      final Freezable possibleGrid = autoBuffer.get();
      assertTrue(possibleGrid instanceof Grid);
      return (Grid) possibleGrid;
      
    }
  }


  /**
   * Failed parameters related to an existing model should be retained after repeated launch of grid search
   */
  @Test
  public void testFailedParamsRetention() {
    try {
      Scope.enter();
      final Frame trainingFrame = parseTestFile("smalldata/iris/iris_train.csv");
      Scope.track(trainingFrame);

      // Setup random hyperparameter search space
      HashMap<String, Object[]> hyperParms = new HashMap<String, Object[]>() {{
        put("_distribution", new DistributionFamily[]{DistributionFamily.multinomial});
        put("_ntrees", new Integer[]{5});
        put("_max_depth", new Integer[]{2});
        put("_min_rows", new Integer[]{10}); // Invalid hyperparameter, causes model training to fail
        put("_learn_rate", new Double[]{.7});
      }};

      GBMModel.GBMParameters params = new GBMModel.GBMParameters();
      params._train = trainingFrame._key;
      params._response_column = "species";

      Job<Grid> gs = GridSearch.startGridSearch(null, params, hyperParms);
      Scope.track_generic(gs);
      final Grid errGrid = gs.get();
      Scope.track_generic(errGrid);

      assertEquals(1, errGrid.getModelCount());

      Grid.SearchFailure failures = errGrid.getFailures();
      assertEquals(0, failures.getFailureCount());

      errGrid.appendFailedModelParameters(errGrid.getModels()[0]._key, params, new RuntimeException("Test exception"));
      
      failures = errGrid.getFailures();
      assertEquals(1, failures.getFailureCount());

      // Train a second model with modified parameters to forcefully produce a new model
      hyperParms.put("_learn_rate", new Double[]{0.5});
      gs = GridSearch.startGridSearch(errGrid._key, params, hyperParms);
      Scope.track_generic(gs);
      final Grid grid = gs.get();
      Scope.track_generic(grid);

      // There should be no errors, one resulting model in the grid with previously supplied parameters
      assertEquals(2, grid.getModelCount());
      assertTrue(grid.getModels()[0] instanceof GBMModel);
      assertTrue(grid.getModels()[1] instanceof GBMModel);

      final Grid.SearchFailure secondRunFailures = grid.getFailures();
      assertEquals(1, secondRunFailures.getFailureCount());

      final String expectedErr = "Test exception";
      assertTrue(failures.getFailureStackTraces()[0].contains(expectedErr));

    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testGetModelKeys() {
    Grid<?> grid = new Grid<>(null, null, null, null);
    grid.putModel(3, Key.make("2"));
    grid.putModel(2, Key.make("1"));
    grid.putModel(1, Key.make("3"));

    assertArrayEquals(
            new Key[]{Key.make("1"), Key.make("2"), Key.make("3")},
            grid.getModelKeys()
    );
  }

  @Test
  public void gridSearchChecksumMatch() {
    try {
      Scope.enter();

      final Frame trainingFrame = parseTestFile("smalldata/iris/iris_train.csv");
      Scope.track(trainingFrame);

      HashMap<String, Object[]> hyperParms = new HashMap<String, Object[]>() {{
        put("_ntrees", new Integer[]{5, 10});
      }};

      GBMModel.GBMParameters params = new GBMModel.GBMParameters();
      params._train = trainingFrame._key;
      params._response_column = "species";

      Job<Grid> gridSearch = GridSearch.startGridSearch(null, params, hyperParms, 1);
      Scope.track_generic(gridSearch);
      final Grid grid = gridSearch.get();
      Scope.track_generic(grid);
      
      for (Model m : grid.getModels()) {
        Model foundModel = grid.getModel(m._input_parms);
        assertNotNull("Expected to find the model in model cache.", foundModel);
        assertEquals("The cached model is different from the expected model.", m, foundModel);
      }
    } finally {
      Scope.exit();
    }
  }

  @Test // this test is fine as with Cartesian we don't really have early stopping based on max_models ( always train whole hyper space)
  public void testParallelCartesian() {
    try {
      Scope.enter();
      final Frame trainingFrame = parseTestFile("smalldata/iris/iris_train.csv");
      Scope.track(trainingFrame);

      // Setup random hyperparameter search space
      HashMap<String, Object[]> hyperParms = new HashMap<String, Object[]>() {{
        put("_distribution", new DistributionFamily[]{DistributionFamily.multinomial});
        put("_ntrees", new Integer[]{5});
        put("_max_depth", new Integer[]{2});
        put("_min_rows", new Integer[]{10,11,12,13,14});
        put("_learn_rate", new Double[]{.7});
      }};

      GBMModel.GBMParameters params = new GBMModel.GBMParameters();
      params._train = trainingFrame._key;
      params._response_column = "species";

      Job<Grid> gs = GridSearch.startGridSearch(null, params, hyperParms, 2);
      Scope.track_generic(gs);
      final Grid grid1 = gs.get();
      Scope.track_generic(grid1);

      assertEquals(5, grid1.getModelCount());

      // Train a grid with new hyper parameters
      hyperParms.put("_learn_rate", new Double[]{0.5});
      gs = GridSearch.startGridSearch(grid1._key, params, hyperParms, 2);
      Scope.track_generic(gs);
      final Grid grid = gs.get();
      Scope.track_generic(grid);

      int expectedNumberOfModelsFromTwoGrids = 10;
      assertEquals(expectedNumberOfModelsFromTwoGrids, grid.getModelCount());

    } finally {
      Scope.exit();
    }
  }

  @Test
  public void test_parallel_random_search_with_max_models_being_less_than_parallelism() {
    try {
      Scope.enter();
      final Frame trainingFrame = parseTestFile("smalldata/iris/iris_train.csv");
      Scope.track(trainingFrame);

      // Setup random hyperparameter search space
      HashMap<String, Object[]> hyperParms = new HashMap<String, Object[]>() {{
        put("_distribution", new DistributionFamily[]{DistributionFamily.multinomial});
        put("_ntrees", new Integer[]{5});
        put("_max_depth", new Integer[]{2});
        put("_min_rows", new Integer[]{10,11,12,13,14});
        put("_learn_rate", new Double[]{.7});
      }};

      GBMModel.GBMParameters params = new GBMModel.GBMParameters();
      params._train = trainingFrame._key;
      params._response_column = "species";

      GridSearch.SimpleParametersBuilderFactory simpleParametersBuilderFactory = new GridSearch.SimpleParametersBuilderFactory();
      HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria hyperSpaceSearchCriteria = new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria();
      int custom_max_model = 2;
      hyperSpaceSearchCriteria.set_max_models(custom_max_model);

      Job<Grid> gs = GridSearch.startGridSearch(null, params, hyperParms, simpleParametersBuilderFactory, hyperSpaceSearchCriteria, 4);
      Scope.track_generic(gs);
      final Grid grid1 = gs.get();
      Scope.track_generic(grid1);

      assertEquals(custom_max_model, grid1.getModelCount());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void test_parallel_random_search_with_max_models_being_greater_than_parallelism() {
    try {
      Scope.enter();
      final Frame trainingFrame = parseTestFile("smalldata/iris/iris_train.csv");
      Scope.track(trainingFrame);

      // Setup random hyperparameter search space
      HashMap<String, Object[]> hyperParms = new HashMap<String, Object[]>() {{
        put("_distribution", new DistributionFamily[]{DistributionFamily.multinomial});
        put("_ntrees", new Integer[]{5});
        put("_max_depth", new Integer[]{2});
        put("_min_rows", new Integer[]{10,11,12,13,14});
        put("_learn_rate", new Double[]{.7});
      }};

      GBMModel.GBMParameters params = new GBMModel.GBMParameters();
      params._train = trainingFrame._key;
      params._response_column = "species";

      GridSearch.SimpleParametersBuilderFactory simpleParametersBuilderFactory = new GridSearch.SimpleParametersBuilderFactory();
      HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria hyperSpaceSearchCriteria = new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria();
      int custom_max_model = 3;
      hyperSpaceSearchCriteria.set_max_models(custom_max_model);

      Job<Grid> gs = GridSearch.startGridSearch(null, params, hyperParms, simpleParametersBuilderFactory, hyperSpaceSearchCriteria, 2);
      Scope.track_generic(gs);
      final Grid grid1 = gs.get();
      Scope.track_generic(grid1);

      assertEquals(custom_max_model, grid1.getModelCount());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testCanceledModelReporting() {
    try {
      Scope.enter();

      Frame trainingFrame = TestFrameCatalog.oneChunkFewRows();

      Map<String, Object[]> hyperParms = Collections.singletonMap(
        "_cancel_job", new Boolean[]{true}
      );

      DummyModelParameters params = new DummyModelParameters();
      params._train = trainingFrame._key;
      
      Grid grid = GridSearch.startGridSearch(null, params, hyperParms).get();
      Scope.track_generic(grid);

      assertArrayEquals(new String[]{"Job Canceled"}, grid.getFailures().getFailureDetails());
    } finally {
      Scope.exit();
    }
  }


}
