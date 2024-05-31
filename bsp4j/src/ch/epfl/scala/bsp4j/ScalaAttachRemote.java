package ch.epfl.scala.bsp4j;

import org.eclipse.xtext.xbase.lib.Pure;
import org.eclipse.xtext.xbase.lib.util.ToStringBuilder;

/**
 * The debug session will connect to a running process. The DAP client will send the port of the
 * running process later.
 */
@SuppressWarnings("all")
public class ScalaAttachRemote {
  public ScalaAttachRemote() {}

  @Override
  @Pure
  public String toString() {
    ToStringBuilder b = new ToStringBuilder(this);
    return b.toString();
  }

  @Override
  @Pure
  public boolean equals(final Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    return true;
  }

  @Override
  @Pure
  public int hashCode() {
    return 1;
  }
}
