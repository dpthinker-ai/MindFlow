let cy = null;
let hostViewport = { width: 0, height: 0 };
let lastPayload = null;

function postToAndroid(event) {
  if (window.MindFlowGraphBridge && window.MindFlowGraphBridge.postMessage) {
    window.MindFlowGraphBridge.postMessage(JSON.stringify(event));
  }
}

function applyHostViewport() {
  const root = document.getElementById('graph-root');
  if (!root) return;
  if (hostViewport.width > 0) {
    root.style.width = `${hostViewport.width}px`;
    document.documentElement.style.width = `${hostViewport.width}px`;
    document.body.style.width = `${hostViewport.width}px`;
  }
  if (hostViewport.height > 0) {
    root.style.height = `${hostViewport.height}px`;
    document.documentElement.style.height = `${hostViewport.height}px`;
    document.body.style.height = `${hostViewport.height}px`;
    document.body.style.minHeight = `${hostViewport.height}px`;
  }
}

function materializePosition(node) {
  const width = Math.max(hostViewport.width || 0, cy ? cy.width() : 0, 1);
  const height = Math.max(hostViewport.height || 0, cy ? cy.height() : 0, 1);
  return {
    x: width * node.xFraction,
    y: height * node.yFraction,
  };
}

function applyStableViewport(graph, payload) {
  if (!payload) return;
  graph.resize();
  const nodesById = new Map(payload.nodes.map((node) => [node.id, node]));
  graph.nodes().forEach((node) => {
    const source = nodesById.get(node.id());
    if (!source) return;
    node.position(materializePosition(source));
  });
  graph.zoom(1);
  graph.pan({ x: 0, y: 0 });
  console.log(
    'stableViewport',
    'graph=', `${graph.width()}x${graph.height()}`,
    'positions=', JSON.stringify(
      graph.nodes().map((node) => ({ id: node.id(), pos: node.position() })),
    ),
  );
}

window.syncHostViewport = function syncHostViewport(width, height) {
  hostViewport = { width, height };
  applyHostViewport();
  console.log('syncHostViewport', width, height);
  if (cy && lastPayload) {
    applyStableViewport(cy, lastPayload);
  }
};

function ensureGraph() {
  if (cy) return cy;
  applyHostViewport();
  cy = cytoscape({
    container: document.getElementById('graph-root'),
    elements: [],
    style: [
      {
        selector: 'node',
        style: {
          'background-color': 'data(accentColor)',
          'label': 'data(label)',
          'color': '#243145',
          'font-size': 11,
          'text-valign': 'bottom',
          'text-halign': 'center',
          'text-margin-y': 10,
          'width': 18,
          'height': 18,
          'overlay-opacity': 0,
        },
      },
      {
        selector: 'node[isCenter = 1]',
        style: {
          'width': 28,
          'height': 28,
          'font-size': 13,
          'font-weight': 600,
        },
      },
      {
        selector: 'edge',
        style: {
          'curve-style': 'bezier',
          'line-color': '#b6c2d1',
          'width': 'mapData(confidence, 0, 1, 1.5, 3.5)',
          'opacity': 0.85,
        },
      },
    ],
    userZoomingEnabled: true,
    userPanningEnabled: true,
    autoungrabify: true,
    boxSelectionEnabled: false,
    minZoom: 0.6,
    maxZoom: 2.5,
  });

  cy.on('tap', 'node', (event) => {
    postToAndroid({ type: 'nodeClick', conceptId: event.target.id() });
  });

  console.log('graph init');
  postToAndroid({ type: 'viewportReady' });
  return cy;
}

window.renderGraph = function renderGraph(payload) {
  try {
    const graph = ensureGraph();
    lastPayload = payload;
    if (!payload || !payload.centerNodeId) {
      graph.elements().remove();
      console.log('renderGraph skipped empty payload');
      return;
    }

    const elements = [];
    payload.nodes.forEach((node) => {
      elements.push({
        data: { ...node, isCenter: node.isCenter ? 1 : 0 },
        position: materializePosition(node),
      });
    });
    payload.edges.forEach((edge) => {
      elements.push({ data: edge });
    });

    console.log(
      'renderGraph payload',
      'center=', payload.centerNodeId,
      'nodes=', payload.nodes.length,
      'edges=', payload.edges.length,
    );
    graph.elements().remove();
    graph.add(elements);
    graph.layout({ name: 'preset', fit: false, animate: false }).run();

    requestAnimationFrame(() => {
      applyStableViewport(graph, payload);
      setTimeout(() => applyStableViewport(graph, payload), 120);
    });
  } catch (error) {
    postToAndroid({
      type: 'renderError',
      message: String(error && error.message ? error.message : error),
    });
  }
};

window.addEventListener('resize', () => {
  if (!cy || !lastPayload) return;
  console.log('window resize');
  applyStableViewport(cy, lastPayload);
});
