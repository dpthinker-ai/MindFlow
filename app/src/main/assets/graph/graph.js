let cy = null;
let hostViewport = { width: 0, height: 0 };
let lastPayload = null;
const BASE_ZOOM = 1;
const PAN_ENABLE_ZOOM_THRESHOLD = 1.05;
const TAP_RADIUS_PX = 34;
const EMPHASIZED_TAP_RADIUS_PX = 42;
const RETURN_NODE_TAP_RADIUS_PX = 50;
const LABEL_HIT_PADDING_PX = 10;

function nodeTapRadius(node) {
  const emphasis = Number(node.data('emphasis') || 0);
  if (node.data('isReturnNode')) return RETURN_NODE_TAP_RADIUS_PX;
  if (emphasis >= 2) return EMPHASIZED_TAP_RADIUS_PX;
  return TAP_RADIUS_PX;
}

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

function resetGraphViewport(graph) {
  graph.resize();
  graph.zoom(BASE_ZOOM);
  graph.pan({ x: 0, y: 0 });
  graph.userPanningEnabled(false);
}

function rebuildGraph(graph, payload) {
  if (!payload) return;
  const elements = [];
  payload.nodes.forEach((node) => {
    const classes = ['label-bottom'];
    if (!node.isCenter && node.emphasis > 0) {
      if (node.yFraction <= 0.28) {
        classes[0] = 'label-top';
      } else if (node.xFraction <= 0.32) {
        classes[0] = 'label-left';
      } else if (node.xFraction >= 0.68) {
        classes[0] = 'label-right';
      }
    }
    elements.push({
      group: 'nodes',
      data: { ...node, isCenter: node.isCenter ? 1 : 0 },
      classes: classes.join(' '),
      position: materializePosition(node),
    });
  });
  payload.edges.forEach((edge) => {
    elements.push({ group: 'edges', data: edge });
  });

  graph.elements().remove();
  graph.add(elements);
  graph.layout({ name: 'preset', fit: false, animate: false }).run();
}

function applyStableViewport(graph, payload) {
  if (!payload) return;
  resetGraphViewport(graph);
  rebuildGraph(graph, payload);
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

function isPointInsideBox(point, box, padding = 0) {
  if (!box) return false;
  return (
    point.x >= (box.x1 - padding) &&
    point.x <= (box.x2 + padding) &&
    point.y >= (box.y1 - padding) &&
    point.y <= (box.y2 + padding)
  );
}

function bestBoundingBoxMatch(renderedPosition) {
  if (!cy || !renderedPosition) return null;
  let bestNode = null;
  let bestArea = Number.POSITIVE_INFINITY;
  let bestDistance = Number.POSITIVE_INFINITY;
  cy.nodes().forEach((node) => {
    const box = node.renderedBoundingBox({ includeLabels: true, includeOverlays: false });
    if (!isPointInsideBox(renderedPosition, box, LABEL_HIT_PADDING_PX)) return;
    const area = Math.max((box.x2 - box.x1) * (box.y2 - box.y1), 1);
    const position = node.renderedPosition();
    const deltaX = position.x - renderedPosition.x;
    const deltaY = position.y - renderedPosition.y;
    const distance = Math.sqrt((deltaX * deltaX) + (deltaY * deltaY));
    if (area < bestArea || (area === bestArea && distance < bestDistance)) {
      bestArea = area;
      bestDistance = distance;
      bestNode = node;
    }
  });
  return bestNode;
}

function nearestRadiusMatch(renderedPosition) {
  if (!cy || !renderedPosition) return null;
  let bestNode = null;
  let bestScore = Number.POSITIVE_INFINITY;
  cy.nodes().forEach((node) => {
    const position = node.renderedPosition();
    const deltaX = position.x - renderedPosition.x;
    const deltaY = position.y - renderedPosition.y;
    const distance = Math.sqrt((deltaX * deltaX) + (deltaY * deltaY));
    const radius = nodeTapRadius(node);
    if (distance > radius) return;
    const score = distance / radius;
    if (score < bestScore) {
      bestScore = score;
      bestNode = node;
    }
  });
  return bestNode;
}

function findNearestNode(renderedPosition) {
  return bestBoundingBoxMatch(renderedPosition) || nearestRadiusMatch(renderedPosition);
}

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
          'label': 'data(displayLabel)',
          'color': '#35506D',
          'font-size': 10,
          'text-halign': 'center',
          'text-valign': 'bottom',
          'text-margin-y': 8,
          'text-margin-x': 0,
          'width': 14,
          'height': 14,
          'border-width': 1,
          'border-color': '#FFFFFF',
          'overlay-opacity': 0,
          'z-index-compare': 'manual',
          'z-index': 10,
        },
      },
      {
        selector: 'node[isSuggested = 1]',
        style: {
          'background-opacity': 0.76,
          'border-color': '#F8FCFF',
          'color': '#49657F',
        },
      },
      {
        selector: 'node[isReturnNode = 1]',
        style: {
          'background-opacity': 0.96,
          'border-color': '#FFF4D9',
          'border-width': 2,
          'z-index': 26,
        },
      },
      {
        selector: 'node.label-top',
        style: {
          'text-halign': 'center',
          'text-valign': 'top',
          'text-margin-y': -10,
          'text-margin-x': 0,
        },
      },
      {
        selector: 'node.label-left',
        style: {
          'text-halign': 'right',
          'text-valign': 'center',
          'text-margin-x': -10,
          'text-margin-y': 0,
        },
      },
      {
        selector: 'node.label-right',
        style: {
          'text-halign': 'left',
          'text-valign': 'center',
          'text-margin-x': 10,
          'text-margin-y': 0,
        },
      },
      {
        selector: 'node[emphasis = 3]',
        style: {
          'width': 28,
          'height': 28,
          'font-size': 13,
          'font-weight': 600,
          'text-margin-y': 12,
          'border-width': 2,
          'z-index': 30,
        },
      },
      {
        selector: 'node[emphasis = 2]',
        style: {
          'width': 17,
          'height': 17,
          'font-size': 11,
          'font-weight': 500,
          'z-index': 24,
        },
      },
      {
        selector: 'node[emphasis = 1]',
        style: {
          'width': 14,
          'height': 14,
          'font-size': 9,
          'color': '#4F6275',
          'text-margin-y': 6,
          'z-index': 18,
        },
      },
      {
        selector: 'node[emphasis = 0]',
        style: {
          'width': 11,
          'height': 11,
          'font-size': 0,
          'text-opacity': 0,
          'background-opacity': 0.8,
          'z-index': 14,
        },
      },
      {
        selector: 'edge',
        style: {
          'curve-style': 'bezier',
          'line-color': '#A9DEFF',
          'width': 'mapData(confidence, 0, 1, 1.4, 3.0)',
          'opacity': 0.9,
          'z-index': 4,
        },
      },
      {
        selector: 'edge[isSuggested = 1]',
        style: {
          'line-style': 'dashed',
          'line-dash-pattern': [6, 5],
          'line-color': '#CBEAFF',
          'opacity': 0.72,
          'width': 1.6,
        },
      },
    ],
    userZoomingEnabled: true,
    userPanningEnabled: false,
    autoungrabify: true,
    boxSelectionEnabled: false,
    minZoom: 0.6,
    maxZoom: 2.5,
  });

  cy.on('tap', 'node', (event) => {
    postToAndroid({ type: 'nodeClick', conceptId: event.target.id() });
  });

  cy.on('tap', (event) => {
    if (event.target !== cy) return;
    const nearestNode = findNearestNode(event.renderedPosition);
    if (nearestNode) {
      postToAndroid({ type: 'nodeClick', conceptId: nearestNode.id() });
    }
  });

  cy.on('zoom', () => {
    cy.userPanningEnabled(cy.zoom() > PAN_ENABLE_ZOOM_THRESHOLD);
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

    console.log(
      'renderGraph payload',
      'center=', payload.centerNodeId,
      'nodes=', payload.nodes.length,
      'edges=', payload.edges.length,
    );
    resetGraphViewport(graph);
    rebuildGraph(graph, payload);
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
