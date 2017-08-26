package solver;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;
import play.Logger;

public class C7Solver {
    Map<Integer, String> nodes = new TreeMap<>();
    List<Edge> edges = new ArrayList<>();
    int[] parent, rank; // union find

    static class Edge {
        public int u, v;
        public double w;

        Edge (int u, int v, double w) {
            this.u = u;
            this.v = v;
            this.w = w;
        }
    }
    
    public C7Solver () {
    }

    public void solve (String file, OutputStream os) throws Exception {
        solve (new FileInputStream (file), os);
    }

    public void load (InputStream is) throws Exception {
        BufferedReader br = new BufferedReader (new InputStreamReader (is));
        br.readLine(); // skip header
        
        StringBuilder[] toks = new StringBuilder[]{
            new StringBuilder (),
            new StringBuilder (),
            new StringBuilder ()
        };

        edges.clear();
        nodes.clear();
        
        Map<String, Integer> lut = new HashMap<>();
        for (String line; (line = br.readLine()) != null;) {
            boolean parity = false;
            for (int i = 0, j = 0; i < line.length(); ++i) {
                char ch = line.charAt(i);
                switch (ch) {
                case '"':
                    parity = !parity;
                    break;
                    
                case ',':
                    if (parity) {
                        toks[j].append(ch);
                    }
                    else
                        ++j;
                    break;
                    
                default:
                    toks[j].append(ch);
                }
            }

            String g = toks[0].toString();
            Integer i = lut.get(g);
            if (i == null) {
                i = lut.size();
                nodes.put(i, g);
                lut.put(g, i);
            }

            String d = toks[1].toString();
            Integer j = lut.get(d);
            if (j == null) {
                j = lut.size();
                lut.put(d, j);
                nodes.put(j, d);
            }

            Edge e = new Edge (i, j, Double.parseDouble(toks[2].toString()));
            edges.add(e);
            
            for (StringBuilder t : toks)
                t.setLength(0);
        }
        lut.clear();
        
        Collections.sort(edges, (a,b) -> {
                if (a.w > b.w) return 1;
                else if (a.w < b.w) return -1;
                else {
                    int d = a.u - b.u;
                    if (d == 0)
                        d = a.v - b.v;
                    return d;
                }
            });
        
        parent = new int[nodes.size()];
        rank = new int[nodes.size()];
        for (int i = 0; i < parent.length; ++i)
            parent[i] = i;
        
        br.close();
    }
    
    public void solve (InputStream is, OutputStream os) throws Exception {
        load (is);
        kruskal (os);
    }

    int find (int n) {
        int p = parent[n];
        while (p != n) {
            n = p;
            p = parent[p];
        }
        return p;
    }

    void union (int i, int j) {
        int pi = find (i);
        int pj = find (j);
        if (pi == pj)
            ;
        else if (rank[pi] < rank[pj])
            parent[pi] = pj;
        else if (rank[pi] > rank[pj])
            parent[pj] = pi;
        else {
            parent[pj] = pi;
            ++rank[pi];
        }
    }
    
    void kruskal (OutputStream os) {
        PrintStream ps = new PrintStream (os);
        ps.println("\"Gene\",\"Disease\",\"Score\"");
        BitSet mst = new BitSet (nodes.size());
        double cost = 0.;
        for (Edge e : edges) {
            if (find (e.u) != find (e.v)) {
                cost += e.w;
                union (e.u, e.v);
                mst.set(e.u);
                mst.set(e.v);
                ps.println("\""+nodes.get(e.u)+"\",\""+nodes.get(e.v)+"\","
                           +e.w);
            }
        }
        System.out.println("## Kruskal MST = "+cost+" spanning "
                           +mst.cardinality()+" nodes");
    }

    public static void main (String[] argv) throws Exception {
        if (argv.length == 0) {
            System.err.println("Usage: solver.C7Solver FILE [OUTPUT]");
            System.exit(1);
        }

        C7Solver c7 = new C7Solver ();
        OutputStream out = System.out;
        if (argv.length > 1) {
            System.out.println("Writing output to "+argv[1]+"...");
            out = new FileOutputStream (argv[1]);
        }
        
        c7.solve(argv[0], out);
        if (out != System.out)
            out.close();
    }
}
