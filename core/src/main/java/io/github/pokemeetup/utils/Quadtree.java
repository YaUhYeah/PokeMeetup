package io.github.pokemeetup.utils;

import com.badlogic.gdx.math.Rectangle;
import java.util.ArrayList;
import java.util.List;

public class Quadtree<T> {
    private static final int MAX_OBJECTS = 10;
    private static final int MAX_LEVELS = 5;

    private int level;
    private List<QuadObject<T>> objects;
    private Rectangle bounds;
    private Quadtree<T>[] nodes;

    private static class QuadObject<T> {
        T object;
        Rectangle bounds;

        QuadObject(T object, Rectangle bounds) {
            this.object = object;
            this.bounds = bounds;
        }
    }

    public Quadtree(int level, Rectangle bounds) {
        this.level = level;
        this.objects = new ArrayList<>();
        this.bounds = bounds;
        this.nodes = new Quadtree[4];
    }

    public void clear() {
        objects.clear();
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i] != null) {
                nodes[i].clear();
                nodes[i] = null;
            }
        }
    }

    private void split() {
        float subWidth = bounds.width / 2;
        float subHeight = bounds.height / 2;
        float x = bounds.x;
        float y = bounds.y;

        nodes[0] = new Quadtree<>(level + 1, new Rectangle(x + subWidth, y, subWidth, subHeight));
        nodes[1] = new Quadtree<>(level + 1, new Rectangle(x, y, subWidth, subHeight));
        nodes[2] = new Quadtree<>(level + 1, new Rectangle(x, y + subHeight, subWidth, subHeight));
        nodes[3] = new Quadtree<>(level + 1, new Rectangle(x + subWidth, y + subHeight, subWidth, subHeight));
    }

    private int getIndex(Rectangle objectBounds) {
        int index = -1;
        double verticalMidpoint = bounds.x + bounds.width / 2;
        double horizontalMidpoint = bounds.y + bounds.height / 2;

        boolean topQuadrant = objectBounds.y < horizontalMidpoint &&
            objectBounds.y + objectBounds.height < horizontalMidpoint;
        boolean bottomQuadrant = objectBounds.y > horizontalMidpoint;

        if (objectBounds.x < verticalMidpoint &&
            objectBounds.x + objectBounds.width < verticalMidpoint) {
            if (topQuadrant) {
                index = 1;
            } else if (bottomQuadrant) {
                index = 2;
            }
        } else if (objectBounds.x > verticalMidpoint) {
            if (topQuadrant) {
                index = 0;
            } else if (bottomQuadrant) {
                index = 3;
            }
        }

        return index;
    }

    public void insert(T object, Rectangle objectBounds) {
        if (nodes[0] != null) {
            int index = getIndex(objectBounds);
            if (index != -1) {
                nodes[index].insert(object, objectBounds);
                return;
            }
        }

        objects.add(new QuadObject<>(object, objectBounds));

        if (objects.size() > MAX_OBJECTS && level < MAX_LEVELS) {
            if (nodes[0] == null) {
                split();
            }

            int i = 0;
            while (i < objects.size()) {
                int index = getIndex(objects.get(i).bounds);
                if (index != -1) {
                    nodes[index].insert(objects.get(i).object, objects.get(i).bounds);
                    objects.remove(i);
                } else {
                    i++;
                }
            }
        }
    }

    public List<T> retrieve(List<T> returnObjects, Rectangle area) {
        if (nodes[0] != null) {
            int index = getIndex(area);
            if (index != -1) {
                nodes[index].retrieve(returnObjects, area);
            } else {
                // Area overlaps multiple quadrants
                for (Quadtree<T> node : nodes) {
                    if (node != null && node.bounds.overlaps(area)) {
                        node.retrieve(returnObjects, area);
                    }
                }
            }
        }

        for (QuadObject<T> obj : objects) {
            if (obj.bounds.overlaps(area)) {
                returnObjects.add(obj.object);
            }
        }

        return returnObjects;
    }
}
