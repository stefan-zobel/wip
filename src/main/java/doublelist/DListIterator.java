package doublelist;

public interface DListIterator extends DIterator {

    public boolean hasPrevious();

    public double previous();

    public int nextIndex();

    public int previousIndex();

    public void set(double e);

    public void add(double e);
}
