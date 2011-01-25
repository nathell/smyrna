(function() {
  var Concordance, getQuery, handler, matchBody, matchWindow, matches, resizeFrame, scrollNext, setInfo, show;
  matchWindow = function() {
    return $($('#output').get(0).contentWindow);
  };
  matchBody = function() {
    return $(matchWindow().get(0).document.body);
  };
  matches = function() {
    return matchBody().find('.smyrna-match');
  };
  window.matches = matches;
  window.matchBody = matchBody;
  Concordance = Backbone.Model.extend({
    nextMatch: function() {
      var current, next;
      current = this.get('selectedMatch');
      next = (current != null) && current + 1 < matches().length ? current + 1 : 0;
      return this.set({
        selectedMatch: next
      });
    },
    prevMatch: function() {
      var current, next;
      current = this.get('selectedMatch');
      next = matches().length === 0 ? null : current > 0 ? current - 1 : matches().length - 1;
      return this.set({
        selectedMatch: next
      });
    }
  });
  window.concordance = new Concordance({
    selectedMatch: null
  });
  concordance.bind('change:selectedMatch', function(model, i) {
    var newMatch;
    matchBody().find('.highlighted-match').removeClass('highlighted-match');
    newMatch = matches().get(i);
    if (i != null) {
      $(newMatch).addClass('highlighted-match');
    }
    return matchWindow().scrollTo(newMatch);
  });
  $.smyrnaCall = function(method, params, callback) {
    return $.jsonRpc({
      url: "http://localhost:8080/json-rpc",
      type: "POST",
      method: method,
      params: params,
      success: function(obj) {
        if (!obj.error) {
          return callback(obj.result);
        }
      }
    });
  };
  getQuery = function() {
    return $('#q').val();
  };
  setInfo = function(info) {
    if (!info) {
      info = 'Wpisz formę podstawową słowa, które chcesz wyszukać';
    }
    return $('#info').text(info);
  };
  show = function(content) {
    var link;
    matchBody().html(content.html);
    link = matchWindow().get(0).document.createElement('link');
    $(link).attr({
      href: document.location.href + 'results.css',
      rel: 'stylesheet',
      type: 'text/css'
    });
    matchBody().append(link);
    if (getQuery() !== '') {
      setInfo(matches().length + ' wyników w dokumencie');
    }
    return matchBody().find('a').click(function() {
      return false;
    });
  };
  handler = function() {
    return $.smyrnaCall('highlight', [getQuery()], show);
  };
  resizeFrame = function() {
    console.log($('.lower td').height());
    return $('#output').height($('.lower td').height());
  };
  scrollNext = function() {
    return concordance.nextMatch;
  };
  $(function() {
    $('#q').val('').keyup(handler).focus();
    $('window').resize(resizeFrame);
    $('#next').click(function() {
      return concordance.nextMatch();
    });
    $('#prev').click(function() {
      return concordance.prevMatch();
    });
    resizeFrame();
    return setInfo();
  });
}).call(this);
