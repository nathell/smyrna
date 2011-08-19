# -*- tab-width: 2; -*-

# linguistic tools

pluralize = (n, f1, f2, f3) ->
  if n is 1
    f1
  else
    if n % 10 in [2, 3, 4] and Math.floor((n % 100) / 10) isnt 1
      f2
    else
      f3

# JSON-RPC

makeURL = (x) -> document.location.protocol + '//' + document.location.host + "/" + x

window.smyrnaCall = (method, params, callback) ->
  $.jsonRpc
    url: makeURL "json-rpc"
    type: "POST"
    method: method
    params: params
    success: (obj) ->
               callback(obj.result) if not obj.error

# Utils

matchWindow = -> $($('#output').get(0).contentWindow)
matchBody = -> $(matchWindow().get(0).document.body)
matches = -> matchBody().find('.smyrna-match')

# Models

Concordance = Backbone.Model.extend
  sset: (m) -> this.set m, silent: true
  nextMatch: ->
    current = this.get 'selectedMatch'
    next = if current? and current + 1 < matches().length then current + 1 else 0
    this.set selectedMatch: next
  prevMatch: ->
    current = this.get 'selectedMatch'
    next = if matches().length == 0 then null else
           if current > 0 then current - 1 else matches().length - 1
    this.set selectedMatch: next
  nextDocument: ->
    current = this.get 'selectedDocument'
    num = this.get 'numDocuments'
    next = if num == 0 then 0 else
           if current == num - 1 then 0 else current + 1
    this.set selectedDocument: next
  prevDocument: ->
    current = this.get 'selectedDocument'
    num = this.get 'numDocuments'
    next = if num == 0 then 0 else
           if current == 0 then num - 1 else current - 1
    this.set selectedDocument: next

window.concordance = new Concordance
  selectedMatch: null
  selectedDocument: 0
  numDocuments: 0
  query: ""
  corpora: null
  currentCorpus: null
  newCorpusDialogState: true # true: dialog proper, false: progress bar

concordance.bind 'change:selectedMatch', (model, i) ->
  matchBody().find('.highlighted-match').removeClass('highlighted-match')
  newMatch = matches().get(i)
  $(newMatch).addClass('highlighted-match') if i?
  matchWindow().scrollTo newMatch, 'fast'

concordance.bind 'change:selectedDocument', (model, document) ->
  model.sset selectedMatch: null
  refreshQuery()

concordance.bind 'change:query', (model, query) ->
  model.sset selectedDocument: 0, selectedMatch: null
  refreshQuery()

concordance.bind 'change:newCorpusDialogState', (model, d) ->
  if d
    $('#new-corpus-modal .prompt').show()
    $('#new-corpus-modal .wait').hide()
  else
    $('#new-corpus-modal .prompt').hide()
    $('#new-corpus-modal .wait').show()

concordance.bind 'change:corpora', (model, corpora) ->
  if corpora.length == 0
    res = '<p>Brak zdefiniowanych korpusów.</p>'
  else
    res = '<p>Kliknięcie w nazwę korpusu spowoduje wybranie go do przeszukiwania.</p>'
    res += '<table>'
    res += '<tr><th>Nazwa korpusu</th><th>Liczba dokumentów</th></tr>'
    res += _.map(corpora, (x) -> '<tr><td><a href="#" class="select-corpus-link">' + x.name + '</a></td><td>' + x.files + ' (<a href="#" class="update-corpus-link">odśwież</a>)</td></tr>').join ''
    res += '</table>'
  $('#current-corpus').empty()
  _.each corpora, (corpus) ->
    $('#current-corpus').append $('<option></option>').val(corpus.name).text(corpus.name)
  if corpora.length == 0
    $('#current-corpus').append $('<option>[brak korpusu]</option>')
  if model.get('currentCorpus') == null and corpora.length > 0
    model.set currentCorpus: corpora[0].name
  $('#corpora').html res
  $('#corpora a.select-corpus-link').click (x) ->
    model.set currentCorpus: $(this).text()
    showTab 'konkordancje'
  $('#corpora a.update-corpus-link').click (x) ->
    concordance.set newCorpusDialogState: false
    $('#new-corpus-modal').reveal()
    smyrnaCall 'update-corpus', [$(this).parent().prev().find('a').text()], (x) ->
      concordance.set newCorpusDialogState: true
      $('.reveal-modal-bg').trigger 'click.modalEvent'
      updateCorporaList()

concordance.bind 'change:currentCorpus', (model, corpus) ->
  $('#current-corpus').val corpus
  refreshQuery()
  updateFrequencyList()

updateFrequencyList = ->
  smyrnaCall 'frequency-list', [concordance.get('currentCorpus')], (data) ->
    mydata = _.map(data, (x) -> {lexeme: x[0], count: x[1]})
    window.mygrid = new Slick.Grid '#freqtable',
      mydata,
      [{id: "clexeme", name: "Leksem", field: 'lexeme', width: 600, sortable: true},
       {id: "ccount", name: "Liczba wystąpień", field: 'count', width: 180, sortable: true}],
      {enableColumnReorder: false}
    window.mygrid.onSort.subscribe (e, args) ->
      compare = (a, b) ->
        aa = a[args.sortCol.field]
        bb = b[args.sortCol.field]
        if aa > bb
          1
        else if aa < bb
          -1
        else
          0
      if args.sortAsc
        mydata.sort(compare)
      else
        mydata.reverse(compare)
      window.mygrid.invalidateAllRows()
      window.mygrid.render()

# Rest

setInfo = (info) ->
  info = 'Wpisz formę podstawową słowa, które chcesz wyszukać' if not info
  $('#info').html(info)

refreshQuery = -> smyrnaCall 'highlight', [concordance.get('currentCorpus'), concordance.get('query'), concordance.get('selectedDocument')], show

show = (content) ->
  matchBody().html content.html
  concordance.set numDocuments: content.count
  link = matchWindow().get(0).document.createElement('link')
  $(link).attr
    href: makeURL 'results.css'
    rel: 'stylesheet'
    type: 'text/css'
  matchBody().append(link)
  if concordance.get('query') != ''
    if content.count == 0
      setInfo('Nie znaleziono dokumentów')
    else
      setInfo('Znaleziono ' + content.numword + ' ' +
              pluralize(content.numword, 'wynik', 'wyniki', 'wyników') + ' w ' + content.count + ' ' +
              pluralize(content.count, 'dokumencie', 'dokumentach', 'dokumentach') + '<br>' +
              matches().length + ' ' + pluralize(matches().length, 'wynik', 'wyniki', 'wyników') + ' w dokumencie ' + (1 + concordance.get 'selectedDocument'))
  matchBody().find('a').click(() -> false)

resizeFrame = () ->
  $('#content').height($(window).height() - 41)
  $('#output').height($('.lower td').height() - 1)

showTab = (tab) ->
  tab = tab.toLowerCase()
  if tab in ['konkordancje', 'frekwencja'] and concordance.get('currentCorpus') is null
    return
  $('.nav li').removeClass 'selected'
  $('#mi-' + tab).addClass 'selected'
  $('#content').children().hide()
  $('#' + tab).show()
  if tab == 'konkordancje'
    $('#q').focus()
    resizeFrame()
  if tab == 'frekwencja'
    updateFrequencyList()

getName = (f) ->
  f = f.substring 0, f.length - 1
  f = f.split /[\\\/]/
  f[f.length - 1]

window.updateCorporaList = () ->
  smyrnaCall 'get-corpora', [], (corpora) -> concordance.set corpora: corpora

$ ->
  if $.browser.msie and $.browser.version.match /^[67]/
    $('body').empty().append $ '<div style="padding: 1em">
<h1>Używasz przestarzałej przeglądarki</h1>
<p>Smyrna nie obsługuje przeglądarki Internet Explorer. Aby używać Smyrny, zainstaluj jedną z zalecanych przeglądarek:</p>
<ul><li><a href="http://www.google.com/chrome/?hl=pl">Google Chrome</a></li>
  <li><a href="http://firefox.pl">Firefox</a></li></ul>
  <p><a href="http://browsehappy.pl">Dowiedz się więcej</a> o powodach, dla których warto zaktualizować przeglądarkę.</p>
</div>'
    return true
  $('#q').val('').keyup(-> concordance.set query: $(this).val())
  $('window').resize(resizeFrame)
  $('#next').click(-> concordance.nextMatch())
  $('#prev').click(-> concordance.prevMatch())
  $('#pnext').click(-> concordance.nextDocument())
  $('#pprev').click(-> concordance.prevDocument())
  $('#content').children().hide()
  resizeFrame()
  setInfo()
  $('.nav li.item').each((k, v) -> $(v).attr(id: 'mi-' + $(v).text().toLowerCase()).html('<a href="#">' + $(v).text() + '</a>'))
  $('.nav .about').click(-> $('#about-modal').reveal())
  $('.nav .tab').click(-> showTab $(this).text())
  $('.formtable td:first').width $('.formtable span').width() + 10
  $('#dir-selector').fileTree {acceptDirs: true, root: '', script: makeURL 'dir'}, (f) ->
    $('#chosen-dir').text f
    $('#corpus-name').val getName f
    $('#create-corpus').removeAttr 'disabled'
  $('#add-corpus').click ->
    $('#create-corpus').attr 'disabled', 'disabled'
    $('#new-corpus-modal').reveal()
  $('#current-corpus').change ->
    concordance.set currentCorpus: $(this).val()
  $('#about-modal button').click ->
    $('.close-reveal-modal').trigger 'click.modalEvent'
  $('#create-corpus').click ->
    concordance.set newCorpusDialogState: false
    smyrnaCall 'add-corpus', [$('#corpus-name').val(), $('#chosen-dir').text(), false], (x) ->
      concordance.set newCorpusDialogState: true
      if typeof x == 'string'
        alert x
      else
        $('.reveal-modal-bg').trigger 'click.modalEvent'
        updateCorporaList()
  updateCorporaList()
  showTab 'korpusy'
