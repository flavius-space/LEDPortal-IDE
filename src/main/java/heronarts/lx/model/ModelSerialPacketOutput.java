package heronarts.lx.model;

import heronarts.lx.LX;
import heronarts.lx.output.SerialPacketOutput;

/**
 * This helper is used for sending dynamic serial packets / datagrams that are
 * specified in the model. Any time the model is changed, this set will be
 * updated.
 */
public class ModelSerialPacketOutput extends SerialPacketOutput implements LX.Listener {
  // TODO(dev): Is this necessary?

  public ModelSerialPacketOutput(LX lx) {
    super(lx);
    setModel(lx.getModel());
    lx.addListener(this);
  }

  @Override
  public void modelChanged(LX lx, LXModel model) {
    // We have a new model, use that instead...
    setModel(model);
  }

  private void setModel(LXModel model) {
    // Clear out all the packets / datagrams from the old model
    this.packets.clear();
    // Recursively add all dynamic packets attached to this model
    if (model != null) {
      addPackets(model);
    }
  }

  private void addPackets(LXModel model) {
    // Depth-first, a model's children are sent before its own datagram
    for (LXModel child : model.children) {
      addPackets(child);
    }
    // Then send the packets for the model itself. For instance, this makes it possible
    // for a parent to send an ArtSync or something after all children send ArtDmx
    if(SerialModel.class.isInstance(model)) {
      addPackets(((SerialModel)model).packets);
    }
  }
}
