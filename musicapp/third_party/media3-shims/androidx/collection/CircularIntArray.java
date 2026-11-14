/*
 * Sonora: minimal replacement for androidx.collection.CircularIntArray, only the methods Media3 uses.
 */
package androidx.collection;

public final class CircularIntArray {
  private int[] elements = new int[8];
  private int head;
  private int size;

  public void addLast(int e) {
    if (size == elements.length) {
      int[] grown = new int[elements.length * 2];
      for (int i = 0; i < size; i++) grown[i] = elements[(head + i) % elements.length];
      elements = grown;
      head = 0;
    }
    elements[(head + size) % elements.length] = e;
    size++;
  }

  public int popFirst() {
    if (size == 0) throw new ArrayIndexOutOfBoundsException();
    int e = elements[head];
    head = (head + 1) % elements.length;
    size--;
    return e;
  }

  public void clear() {
    head = 0;
    size = 0;
  }

  public boolean isEmpty() {
    return size == 0;
  }

  public int size() {
    return size;
  }
}
