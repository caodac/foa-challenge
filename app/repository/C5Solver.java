package repository;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;
import play.Logger;

/**
 * sbt "runMain repository.C5Solver GRAPH..."
 */
public class C5Solver {
    long[] nodes;
    int[][] edges;
    Map<String, BitSet> vprops = new TreeMap<>();
    Map<String, BitSet> eprops = new TreeMap<>();
    Map<Long, Integer> lut = new HashMap<>();
    int[] rank, parent;
    int[][] next;
    int nv, ne;
        
    public C5Solver () {
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

    public void load (InputStream is) throws IOException {
        BufferedReader br = new BufferedReader (new InputStreamReader (is));
        String line = br.readLine();
        String[] toks = line.split("\\s");
        if (toks.length != 2)
            throw new IllegalArgumentException ("Not a valid graph format");
        
        nv = Integer.parseInt(toks[0]);
        ne = Integer.parseInt(toks[1]);
        Logger.debug("Loading graph "+nv+" nodes "+ne+" edges...");
        
        vprops.clear();
        nodes = new long[nv];
        rank = new int[nv];
        parent = new int[nv];
        lut.clear();
        int[][] dist = new int[nv][nv];
        for (int i = 0; i < nv; ++i) {
            line = br.readLine();
            if (line == null)
                throw new IllegalArgumentException ("Premature end of file");
            toks = line.split("\\s");
            nodes[i] = Long.parseLong(toks[0]);
            for (int j = 1; j < toks.length; ++j) {
                BitSet bs = vprops.get(toks[j]);
                if (bs == null)
                    vprops.put(toks[j], bs = new BitSet (nv));
                bs.set(i);
            }
            lut.put(nodes[i], i);
            parent[i] = i;
            for (int j = 0; j < i; ++j)
                dist[j][i] = dist[i][j] = nv+1;
        }

        eprops.clear();
        edges = new int[nv][nv];
        next = new int[nv][nv];
        Map<String, Integer> edgeLabels = new TreeMap<>();
        for (int i = 1; i <= ne; ++i) {
            line = br.readLine();
            if (line == null)
                throw new IllegalArgumentException ("Premature end of file");
            toks = line.split("\\s");
            long a = Long.parseLong(toks[0]);
            long b = Long.parseLong(toks[1]);
            Integer na = lut.get(a);
            Integer nb = lut.get(b);
            if (na == null) {
                Logger.warn("Unknown node "+a+" in edge ("+a+","+b+")");
            }
            else if (nb == null) {
                Logger.warn("Unknown node "+b+" in edge ("+a+","+b+")");
            }
            else {
                union (na, nb);
                dist[na][nb] = dist[nb][na] = 1;
                next[na][nb] = nb;
                next[nb][na] = na;
                for (int j = 2; j < toks.length; ++j) {
                    BitSet bs = eprops.get(toks[j]);
                    if (bs == null)
                        eprops.put(toks[j], bs = new BitSet (ne+1));
                    bs.set(i);
                    Integer c = edgeLabels.get(toks[j]);
                    edgeLabels.put(toks[j], c==null ? 1 : c+1);
                }
                edges[na][nb] = edges[nb][na] = i;
            }
        }

        for (int k = 0; k < nv; ++k)
            for (int i = 0; i < nv; ++i)
                for (int j = 0; j < nv; ++j) {
                    int d = dist[i][k] + dist[k][j];
                    if (dist[i][j] > d) {
                        dist[i][j] = dist[j][i] = d;
                        next[i][j] = next[i][k];
                        next[j][i] = next[j][k];
                    }
                }

        int maxdist = 0, maxi = -1, maxj = -1;
        List<int[]> maxpairs = new ArrayList<>();
        for (int i = 0; i < nv; ++i)
            for (int j = 0; j < i; ++j) {
                if (dist[i][j] <= nv) {
                    if (dist[i][j] > maxdist) {
                        maxdist = dist[i][j];
                        maxpairs.clear();
                        maxpairs.add(new int[]{i,j});
                    }
                    else if (dist[i][j] == maxdist) {
                        maxpairs.add(new int[]{i,j});
                    }
                }
            }
        
        // maxdist is the number of edges; +1 for the node count
        System.out.println("Max dist (diameter): "+(maxdist+1));
        for (int[] p : maxpairs) {
            System.out.print(nodes[p[0]]+" "+nodes[p[1]]+" => ");
            System.out.print(nodes[p[0]]);
            for (int v = p[0]; v != p[1];) {
                v = next[v][p[1]];
                System.out.print(" "+nodes[v]);
            }
            System.out.println();
        }
        
        Map<Integer, Integer> cc = new TreeMap<>();
        for (int i = 0; i < nv; ++i) {
            int p = find (i);
            Integer c = cc.get(p);
            cc.put(p, c == null ? 1 : c+1);
        }

        System.out.println
            ("Graph loaded; "+nv+" nodes with "+vprops.size()+" properties and "
             +ne+" edges with "+eprops.size()+" properties!");
        int maxd = 0;
        for (int i = 0; i < nv; ++i)
            for (int j = 0; j < nv; ++j)
                if (i != j) {
                    int c  = 0;
                    for (BitSet bs : eprops.values()) {
                        if (bs.get(edges[i][j]))
                            ++c;
                    }
                    if (c > maxd)
                        maxd = c;
                }

        System.out.println("Node with maximum degree: "+maxd);
        System.out.println("Graph has "+cc.size()
                           +" connected componented; max component size is "
                           +cc.values().stream().mapToInt(x->x)
                           .max().getAsInt());

        System.out.println("Maximal cliques:");

        Set<String> maxLabels = new TreeSet<>();
        int max = 0;
        for (Map.Entry<String, Integer> me : edgeLabels.entrySet()) {
            if (me.getValue() > max) {
                maxLabels.clear();
                max = me.getValue();
                maxLabels.add(me.getKey());
            }
            else if (me.getValue() == max) {
                maxLabels.add(me.getKey());
            }
        }
        Logger.debug("Max labels: "+max+" => "+ maxLabels);
        
        bronKerbosch (c -> {
                Set<String> props = null;
                for (int i = c.nextSetBit(0);
                     i >= 0; i = c.nextSetBit(i+1)) {
                    for (int j = c.nextSetBit(0);
                         j >= 0; j = c.nextSetBit(j+1)) {
                        if (i != j) {
                            Set<String> set = new TreeSet<>();
                            for (Map.Entry<String, BitSet> me:
                                     eprops.entrySet()) {
                                if (me.getValue().get(edges[i][j]))
                                    set.add(me.getKey());
                            }
                            
                            if (props == null) {
                                props = set;
                            }
                            else {
                                props.retainAll(set);
                            }
                            /*
                              Logger.debug(nodes[i]+","+nodes[j]+": "
                              +set+"\n => "+props);
                            */
                        }
                    }
                }
                
                // a clique is valid only if there is one or more properties
                // that span the clique!
                if (props != null && !props.isEmpty()) {
                    System.out.println("Clique found: "+c.cardinality()+" =>");
                    for (int i = c.nextSetBit(0);
                         i >= 0; i = c.nextSetBit(i+1)) {                   
                        System.out.print(" "+nodes[i]);
                    }
                    System.out.println(" => "+props);
                }
            } /*,"E514"*/ /*maxLabels.toArray(new String[0])*/ );
    }

    void bronKerbosch (Consumer<BitSet> visitor,  String... labels) {
        BitSet[] G = new BitSet[nv];
        for (int i = 0; i < nv; ++i)
            G[i] = new BitSet (nv);

        BitSet bs = null;
        for (String l : labels) {
            BitSet e = eprops.get(l);
            if (e == null) {
                Logger.warn("Bogus edge label: "+l);
            }
            else {
                if (bs == null) bs = (BitSet)e.clone();
                else bs.and(e);
            }
        }
        
        int cardinality = 0;
        for (int i = 0; i < nv; ++i) {  
            for (int j = i+1; j < nv; ++j) {
                if (edges[i][j] > 0
                    && (bs == null || bs.isEmpty() || bs.get(edges[i][j]))) {
                    G[i].set(j);
                    G[j].set(i);
                }
            }
            
            if (!G[i].isEmpty())
                ++cardinality;
        }
        
        bronKerbosh (G, cardinality, visitor);
    }

    static void bronKerbosh (BitSet[] G, int cardinality,
                             Consumer<BitSet> visitor) {
        BitSet C = new BitSet (G.length);
        BitSet S = new BitSet (G.length);
        BitSet P = new BitSet (G.length);
        P.set(0, G.length, true);
        
        bronKerbosch (G, cardinality, C, P, S, visitor);
    }
    
    static boolean bronKerbosch (BitSet[] G, int cardinality,
                                 BitSet C, BitSet P, BitSet S,
                                 Consumer<BitSet> visitor) {
        boolean done = false;
        if (P.isEmpty() && S.isEmpty()) {
            BitSet c = (BitSet)C.clone();
            visitor.accept(c);
            done = c.cardinality() >= cardinality;
        }
        else {
            for (int u = P.nextSetBit(0);
                 u >=0 && !done; u = P.nextSetBit(u+1)) {
                P.clear(u);
                BitSet PP = (BitSet)P.clone();
                BitSet SS = (BitSet)S.clone();
                PP.and(G[u]);
                SS.and(G[u]);
                C.set(u);
                done = bronKerbosch (G, cardinality, C, PP, SS, visitor);
                C.clear(u);
                S.set(u);
            }
        }
        return done;
    }

    public static void main (String[] argv) throws Exception {
        if (argv.length == 0) {
            System.err.println("Usage: repository.C5Solver GRAPH");
            System.exit(1);
        }
        
        C5Solver c5 = new C5Solver ();
        for (String a : argv) {
            FileInputStream fis = new FileInputStream (a);
            Logger.debug("Loading file "+a);
            c5.load(fis);
            fis.close();
        }
    }
}


    
