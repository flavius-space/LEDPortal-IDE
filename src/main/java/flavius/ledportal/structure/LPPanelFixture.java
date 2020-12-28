package flavius.ledportal.structure;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import flavius.ledportal.LPMeshable;
import flavius.ledportal.LPPanelModel;
import flavius.ledportal.LPPanelModel.Point;
import flavius.pixelblaze.PBColorOrder;
import flavius.pixelblaze.PBRecordType;
import flavius.pixelblaze.output.PBExpanderDataMessage;
import heronarts.lx.LX;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.output.ArtNetDatagram;
import heronarts.lx.output.DDPDatagram;
import heronarts.lx.output.KinetDatagram;
import heronarts.lx.output.LXBufferOutput;
import heronarts.lx.output.LXOutput;
import heronarts.lx.output.OPCDatagram;
import heronarts.lx.output.OPCOutput;
import heronarts.lx.output.OPCSocket;
import heronarts.lx.output.SerialDefinition;
import heronarts.lx.output.SerialMessage;
import heronarts.lx.output.StreamingACNDatagram;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.DiscreteParameter;
import heronarts.lx.parameter.EnumParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.parameter.StringParameter;
import heronarts.lx.structure.GridFixture.PositionMode;
import heronarts.lx.structure.GridFixture.Wiring;
import heronarts.lx.structure.LXFixture;
import heronarts.lx.structure.SerialProtocolFixture;
import heronarts.lx.transform.LXMatrix;
import processing.data.JSONArray;

public class LPPanelFixture extends SerialProtocolFixture {

  public final EnumParameter<PositionMode> positionMode = new EnumParameter<PositionMode>(
    "Mode", PositionMode.CORNER).setDescription(
      "Whether the arc is positioned by its starting point or center");

  public final EnumParameter<Wiring> wiring = new EnumParameter<Wiring>(
    "Wiring", Wiring.ROWS_L2R_B2T)
      .setDescription("How the strips in the grid are sequentially wired");

  public final BooleanParameter splitPacket = new BooleanParameter(
    "Split Packet", false).setDescription(
      "Whether to break a large grid into multiple packets on separate channels");

  public final DiscreteParameter pointsPerPacket = new DiscreteParameter(
    "Points Per Packet", 170, 1, 21845)
      .setDescription("Number of LED points per packet");

  public final StringParameter pointIndicesJSON = new StringParameter(
    "Point Indices", "[[0,0]]")
      .setDescription("A JSON array of integer points that make up this panel");

  public final BoundedParameter rowSpacing = new BoundedParameter("Row Spacing",
    10, 0, 1000000).setDescription("Spacing between rows in the grid");

  public final BoundedParameter columnSpacing = new BoundedParameter(
    "Column Spacing", 10, 0, 1000000)
      .setDescription("Spacing between columns in the grid");

  public final BoundedParameter rowShear = new BoundedParameter("Row Shear", 0,
    0, 1000000).setDescription("Offset to add to each additional row");

  public final DiscreteParameter globalGridOriginX = new DiscreteParameter(
    "Global Grid Origin X", 0, Integer.MIN_VALUE / 2, Integer.MAX_VALUE / 2)
      .setDescription(
        "The X coordinate in the global grid of the origin index, [0, 0]");

  public final DiscreteParameter globalGridOriginY = new DiscreteParameter(
    "Global Grid Origin Y", 0, Integer.MIN_VALUE / 2, Integer.MAX_VALUE / 2)
      .setDescription(
        "The Y coordinate in the global grid of the origin index, [0, 0]");

  public final StringParameter globalGridMatrix = new StringParameter(
    "Global Grid Matrix", "[[1,0],[0,1]]").setDescription(
      "A JSON integer matrix which transforms local indices to global grid indices");

  /**
   * A transformation to apply to the local grid indices in this fixture to give
   * the global grid indices. This is applied after the local grid indices have
   * been used to position the points.
   *
   */
  int[][] gridTransform = new int[2][3];

  /**
   * Array of (x,y) local grid coordinates from which this fixtures points in 3d
   * are derived.
   */
  int[][] gridIndices;

  /**
   * Array of (x,y) global grid coordinates which are used to place the points
   * from this fixture onto a world grid containing other fixtures.
   */
  int[][] worldGridIndices;

  public LPPanelFixture(LX lx) {
    super(lx, "Panel");
    addParameter("host", this.host);
    addOutputParameter("protocol", this.protocol);
    addOutputParameter("transport", this.transport);
    addOutputParameter("port", this.port);
    addOutputParameter("artNetUniverse", this.artNetUniverse);
    addOutputParameter("opcChannel", this.opcChannel);
    addOutputParameter("ddpDataOffset", this.ddpDataOffset);
    addOutputParameter("kinetPort", this.kinetPort);
    addOutputParameter("splitPacket", this.splitPacket);
    addOutputParameter("pointsPerPacket", this.pointsPerPacket);

    addOutputParameter("serialPort", this.serialPort);
    addOutputParameter("baudRate", this.baudRate);
    addOutputParameter("unknownSerialPort", this.unknownSerialPort);
    addOutputParameter("pixelBlazeChannel", this.pixelBlazeChannel);
    addOutputParameter("serialProtocol", this.serialProtocol);

    addMetricsParameter("wiring", this.wiring);
    addMetricsParameter("reverse", this.reverse);

    addGeometryParameter("pointIndicesJSON", this.pointIndicesJSON);
    addGeometryParameter("globalGridOriginX", this.globalGridOriginX);
    addGeometryParameter("globalGridOriginY", this.globalGridOriginY);
    addGeometryParameter("globalGridMatrix", this.globalGridMatrix);
    addGeometryParameter("rowSpacing", this.rowSpacing);
    addGeometryParameter("columnSpacing", this.columnSpacing);
    addGeometryParameter("rowShear", this.rowShear);
    addGeometryParameter("positionMode", this.positionMode);
  }

  public void regenerateGridTransform() {
    this.gridTransform[0][2] = 0;
    this.gridTransform[1][2] = 0;
    JSONArray parsed = JSONArray.parse(this.globalGridMatrix.getString());
    for (int i = 0; i < parsed.size(); i++) {
      int[] row = parsed.getJSONArray(i).getIntArray();
      for (int j = 0; j < row.length; j++) {
        this.gridTransform[i][j] = row[j];
      }
    }
    this.gridTransform[0][2] = this.globalGridOriginX.getValuei();
    this.gridTransform[1][2] = this.globalGridOriginY.getValuei();
  }

  /**
   * regenerates the gridIndices
   */
  public void regenerateGridIndices() {
    regenerateGridTransform();

    JSONArray parsed = JSONArray.parse(this.pointIndicesJSON.getString());
    int newSize = parsed.size();
    if (this.gridIndices == null || this.gridIndices.length != newSize) {
      this.gridIndices = new int[newSize][2];
    }
    if (this.worldGridIndices == null
      || this.worldGridIndices.length != newSize) {
      this.worldGridIndices = new int[newSize][2];
    }
    for (int i = 0; i < newSize; i++) {
      this.gridIndices[i] = parsed.getJSONArray(i).getIntArray();
      for (int j = 0; j < 2; j++) {
        this.worldGridIndices[i][j] = (this.gridIndices[i][0]
          * this.gridTransform[j][0])
          + (this.gridIndices[i][1] * this.gridTransform[j][1])
          + this.gridTransform[j][2];
      }
    }
  }

  /**
   * Creates point objects, but does not position them. That is left to
   * {@link regeneratePointGeometry}
   */
  protected void regeneratePoints() {
    regenerateGridIndices();
    // We may have a totally new size, blow out the points array and rebuild
    this.mutablePoints.clear();

    int pointIndex = this.firstPointIndex;
    for (int i = 0; i < this.size(); i++) {
      int[] local = this.gridIndices[i];
      int[] world = this.worldGridIndices[i];
      LXPoint p = new Point(world[0], world[1], local[0], local[1], 0, 0, 0);
      p.index = pointIndex++;
      this.mutablePoints.add(p);
    }
  }

  /**
   * Invoked when this fixture has been loaded or added to some container. Will
   * rebuild the points and the metrics, and notify container of the change to
   * this fixture's generation
   */
  @Override
  protected void regenerate() {
    regeneratePoints();

    // A new model will have to be created, forget these points
    this.model = null;
    this.modelPoints.clear();

    // Regenerate our geometry, note that we bypass regenerateGeometry() here
    // because we don't need
    // to notify our container about the change. We're going to notify them
    // after this of even
    // more substantive generation change.
    _regenerateGeometry();

    // Rebuild output objects
    regenerateOutputs();

    onRegenerate();

    // Let our container know that our structural generation has changed
    if (this.container != null) {
      this.container.fixtureGenerationChanged(this);
    }
  }

  /**
   * Constructs an LXModel object for this Fixture
   *
   * @return Model representation of this fixture
   */
  @Override
  public LXModel toModel() {
    // Creating a new model, clear our set of points
    this.modelPoints.clear();

    // Note: we make a deep copy here because a change to the number of points
    // in one fixture will alter point indices in all fixtures after it.
    // When we're in multi-threaded mode, that point might have been passed to
    // the UI, which holds a reference to the model.
    // The indices passed to the UI cannot be changed mid-flight, so we make new
    // copies of all points here to stay safe.
    for (LXPoint p : this.points) {
      modelPoints.add((new Point().set((Point) p)));
    }

    // Now iterate over our children and add their points too
    List<LXModel> childModels = new ArrayList<LXModel>();
    for (LXFixture child : this.children) {
      LXModel childModel = child.toModel();
      for (LXPoint p : childModel.points) {
        this.modelPoints.add(p);
      }
      childModels.add(childModel);
    }

    // Generate any submodels references into of this fixture
    for (Submodel submodel : toSubmodels()) {
      childModels.add(submodel);
    }

    // Okay, good to go, construct the model

    // TODO(Dev): Deal with childModels
    // LXModel model = instantiateModel( this.modelPoints,
    // childModels.toArray(new LXModel[0]), getModelKeys() );

    LPPanelModel model = new LPPanelModel(modelPoints.stream()
      .map(point -> (Point) point).collect(Collectors.toList()));
    model.transform.set(this.geometryMatrix);

    // for (Point p : model.points) {
    // // this.modelPoints.add((LXPoint) new Point(p.xi, p.yi, p.x,
    // // p.y, p.z));
    // this.mutablePoints.add(p);
    // }

    return this.model = model;
  }

  public List<LXParameter> gridIndexParameters = Arrays
    .asList(new LXParameter[] { this.globalGridOriginX, this.globalGridOriginY,
      this.globalGridMatrix, this.pointIndicesJSON });

  @Override
  public void onParameterChanged(LXParameter p) {
    if (gridIndexParameters.contains(p)) {
      regenerateGridIndices();
    }
    super.onParameterChanged(p);
  }

  @Override
  protected int size() {
    if (this.gridIndices == null)
      regenerateGridIndices();
    return this.gridIndices.length;
  }

  private void addOutput(SerialDefinition definition, int[] indexBuffer,
    int channel) {
    LXOutput output = null;
    // TODO: implement PixelBlaze color order based off LXBufferOutput.ByteOrder
    PBColorOrder order = PBColorOrder.RGB;
    switch (this.serialProtocol.getEnum()) {
    case PBX_WS281X:
      output = new PBExpanderDataMessage(lx, indexBuffer,
        PBRecordType.SET_CHANNEL_WS2812, order, channel);
      break;
    case PBX_APA102:
      output = new PBExpanderDataMessage(lx, indexBuffer,
        PBRecordType.SET_CHANNEL_APA102_DATA, order, channel);
      break;
    default:
      LX.error("Undefined serial protocol in LPPanelFixture: "
        + this.serialProtocol.getEnum());
      break;
    }
    if (output != null) {
      output.enabled.setValue(definition != null);
      if (definition != null && output instanceof SerialMessage) {
        ((SerialMessage) output).setDefinition(definition);
      }
      addOutput(output);
    }
  }

  private void addOutput(InetAddress address, int[] indexBuffer, int channel) {
    LXOutput output = null;
    switch (this.protocol.getEnum()) {
    case ARTNET:
      output = new ArtNetDatagram(this.lx, indexBuffer, channel);
      break;
    case SACN:
      output = new StreamingACNDatagram(this.lx, indexBuffer, channel);
      break;
    case OPC:
      switch (this.transport.getEnum()) {
      case TCP:
        output = new OPCSocket(this.lx, indexBuffer, (byte) channel);
        break;
      default:
      case UDP:
        output = new OPCDatagram(this.lx, indexBuffer, (byte) channel);
        break;
      }
      ((OPCOutput) output).setPort(this.port.getValuei());
      break;
    case DDP:
      output = new DDPDatagram(this.lx, indexBuffer, channel);
      break;
    case KINET:
      output = new KinetDatagram(this.lx, indexBuffer, channel);
      break;
    default:
      LX.error(
        "Undefined output protocol in GridFixture: " + this.protocol.getEnum());
      break;
    }
    if (output != null) {
      output.enabled.setValue(address != null);
      if (address != null && (output instanceof LXOutput.InetOutput)) {
        ((LXOutput.InetOutput) output).setAddress(address);
      }
      addOutput(output);
    }
  }

  @Override
  protected void buildOutputs() {

    // Add LXDatagram outputs

    Protocol protocol = this.protocol.getEnum();
    SerialProtocol serialProtocol = this.serialProtocol.getEnum();

    if (protocol == Protocol.NONE && serialProtocol == SerialProtocol.NONE)
      return;

    int[] wiringIndexBuffer = getWiringIndexBuffer();
    if (protocol != Protocol.NONE) {
      InetAddress address = resolveHostAddress();
      int pointsPerPacket = this.pointsPerPacket.getValuei();
      if (this.splitPacket.isOn()
        && (wiringIndexBuffer.length > pointsPerPacket)) {
        int i = 0;
        int channel = getProtocolChannel();
        while (i < wiringIndexBuffer.length) {
          int chunkSize = Math.min(pointsPerPacket,
            wiringIndexBuffer.length - i);
          int chunkIndexBuffer[] = new int[chunkSize];
          System.arraycopy(wiringIndexBuffer, i, chunkIndexBuffer, 0,
            chunkSize);
          addOutput(address, chunkIndexBuffer, channel++);
          i += chunkSize;
        }
      } else {
        addOutput(address, wiringIndexBuffer, getProtocolChannel());
      }
    }

    if (serialProtocol != SerialProtocol.NONE) {
      addOutput(getSerialDefinition(), wiringIndexBuffer,
        getSerialProtocolChannel());
    }
  }

  SerialDefinition getSerialDefinition() {
    String portName = this.serialPort.getString();
    if (portName == "") {
      return null;
    }
    return new SerialDefinition(portName, (int) baudRate.getValue());
  }

  @Override
  protected void computeGeometryMatrix(LXMatrix geometryMatrix) {
    float degreesToRadians = (float) Math.PI / 180;
    float rowSpacing = this.rowSpacing.getValuef();
    float columnSpacing = this.columnSpacing.getValuef();
    float rowShear = this.rowShear.getValuef();
    geometryMatrix.multiply(LPMeshable.p3DToLXMatrix(LPMeshable.matCartToLX));
    geometryMatrix.translate(this.x.getValuef(), this.y.getValuef(),
      this.z.getValuef());
    geometryMatrix.rotateZ(this.yaw.getValuef() * degreesToRadians);
    geometryMatrix.rotateY(this.pitch.getValuef() * degreesToRadians);
    geometryMatrix.rotateX(this.roll.getValuef() * degreesToRadians);
    geometryMatrix.multiply(LPMeshable.p3DToLXMatrix(
      LPMeshable.getRowColShearMatrix(rowSpacing, columnSpacing, rowShear)));
  }

  @Override
  protected void computePointGeometry(LXMatrix matrix, List<LXPoint> points) {
    int size = this.size();
    for (int i = 0; i < size; i++) {
      ((Point) points.get(i)).localIndexTransform(matrix);
    }
  }

  @Override
  protected int[] toDynamicIndexBuffer() {
    if (this.reverse.isOn()) {
      return super.toDynamicIndexBuffer(size() - 1, size(), -1);
    } else {
      return super.toDynamicIndexBuffer();
    }
  }

  @Override
  protected String getModelKey() {
    return "panel";
  }

  private int[] getWiringIndexBuffer() {
    int size = this.size();
    int[] indexBuffer = new int[size];

    if (this.reverse.isOn()) {
      for (int i = 0; i < size; i++) {
        indexBuffer[size - i - 1] = this.points.get(i).index;
      }
    } else {
      for (int i = 0; i < size; i++) {
        indexBuffer[i] = this.points.get(i).index;
      }
    }

    return indexBuffer;
  }

  @Override
  protected void reindexOutputs() {
    int[] wiringIndexBuffer = getWiringIndexBuffer();
    for (LXOutput output : this.outputs) {
      if (output instanceof LXBufferOutput) {
        ((LXBufferOutput) output).updateIndexBuffer(wiringIndexBuffer);
      }
    }
  }

}
