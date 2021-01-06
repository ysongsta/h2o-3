package hex.segments;

import water.test.dummy.DummyModel;
import water.test.dummy.DummyAction;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.test.dummy.DummyModelParameters;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class SegmentModelsBuilderTest extends TestUtil {

  @Parameterized.Parameter
  public Integer parallelism;

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Parameterized.Parameters
  public static Object[] data() {
    return new Object[] { null, 2 };
  }

  @Test
  public void buildSegmentModels() {
    try {
      Scope.enter();
      Frame fr = Scope.track(parseTestFile("./smalldata/junit/iris.csv"));

      Frame segments = new Frame(Key.make());
      segments.add("class", Vec.makeVec(new long[]{2,0,1}, fr.vec("class").domain(), Vec.VectorGroup.VG_LEN1.addVec()));
      DKV.put(segments);
      Scope.track_generic(segments);

      DummyModelParameters parms = new DummyModelParameters();
      parms._makeModel = true;
      parms._action = new GetSegment();
      parms._train = fr._key;
      parms._response_column = "sepal_wid";

      SegmentModelsBuilder.SegmentModelsParameters smParms = new SegmentModelsBuilder.SegmentModelsParameters();
      smParms._segments = segments._key;
      if (parallelism != null)
        smParms._parallelism = parallelism;
      SegmentModels segmentModels = new SegmentModelsBuilder(smParms, parms).buildSegmentModels().get();
      Scope.track_generic(segmentModels);

      Frame smFrame = segmentModels.toFrame();
      Scope.track(smFrame);

      System.out.println(smFrame.toTwoDimTable());

      assertEquals(3, smFrame.numRows());
      Vec segmentVec = smFrame.vec("class");
      Vec.Reader segmentReader = segmentVec. new Reader();
      Vec.Reader modelIdReader = smFrame.vec("model"). new Reader();
      for (int i = 0; i < 3; i++) {
        String segment = segmentVec.domain()[(int) segmentReader.at(i)];
        Key<DummyModel> dmKey = Key.make(modelIdReader.atStr(new BufferedString(), i).toString());
        DummyModel dm = dmKey.get(); 
        assertNotNull(dm);
        assertEquals(segment, dm._output._msg);
        dm.remove();
      }
    } finally {
      Scope.exit();
    }
  }

  private static class GetSegment extends DummyAction<GetSegment> {
    @Override
    protected String run(DummyModelParameters parms) {
      Vec segmentVec = parms.train().vec("class");
      assertTrue(segmentVec.isConst());
      return segmentVec.domain()[(int) segmentVec.at(0)];
    }
  } 
  
}
