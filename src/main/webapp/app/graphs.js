// logical chart units
var CHART_WIDTH = 1000,
    CHART_HEIGHT = 1000;

function createChart(elem) {
  var chart = new Raphael(elem, '100%', '100%');
  chart.setViewBox(0, 0, CHART_WIDTH, CHART_HEIGHT);
  // This isn't supported natively in Raphael
  chart.canvas.setAttribute('preserveAspectRatio', 'none');

  return chart;
}

app.directive('bugRateChart', function() {
  var colors = ['#E63A16', '#153B5F'];

  return {
    restrict: 'E',
    scope: {
      data: '='
    },
    link: function(scope, element, attrs) {
      var data = scope.data;
      var chart = createChart(element[0]);

      var maxValue = Math.max.apply(null, data.map(function (row) {
        return Math.max(row[1], row[2]);
      }));
      // total cols = 2 cols per group + 1-col gap between each group
      var colWidth = CHART_WIDTH / (data.length * 3 - 1);
      data.forEach(function (row, rowIdx) {

        row.slice(1).forEach(function (val, colIdx) {
          var colHeight = val / maxValue * CHART_HEIGHT;
          var hOffset = colWidth * (3 * rowIdx + colIdx);
          var vOffset = CHART_HEIGHT - colHeight;

          var col = chart.rect(hOffset,
              vOffset,
              colWidth,
              colHeight);
          col.attr('fill', colors[colIdx]);
          col.attr('stroke-width', 0);
        });
      });
    }
  };
});

app.directive('bugChart', function() {
  return {
    restrict: 'E',
    scope: {
      data: '='
    },
    link: function(scope, element, attrs) {
      var data = scope.data;

      var chart = createChart(element[0]);

      var maxValue = Math.max.apply(null, data.map(function (row) {
        return row[1];
      }));
      var hOffset = CHART_WIDTH / (data.length - 1);

      var strokeWidth = 10;
      var points = data.map(function (row, idx) {
        var val = row[1];
        return { x: hOffset * idx,
          y: CHART_HEIGHT - (val / maxValue) * CHART_HEIGHT };
      });

      // close the path, allowing for stroke width
      // XXX: this is cheating
      points[0].x -= strokeWidth;
      points[points.length - 1].x += strokeWidth;
      points.push({ x: CHART_WIDTH + strokeWidth, y: CHART_HEIGHT + strokeWidth });
      points.push({ x: -strokeWidth, y: CHART_HEIGHT + strokeWidth });

      // Is this really how paths are created in SVG? :(
      var pathStr =
          'M ' +
              points.map(function (p) { return p.x + ' ' + p.y; }).join(' L ') +
              ' z';

      var line = chart.path(pathStr);
      line.attr({
        'stroke': '#E63A16',
        'stroke-width': strokeWidth,
        'fill': '#5F1A0A'
      });
    }
  }
});
