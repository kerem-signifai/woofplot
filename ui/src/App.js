import util from './util'
import _ from 'lodash';

import React, {Component} from 'react';
import {Button, Checkbox, Dropdown, Grid, Header, Input, Popup} from 'semantic-ui-react';
import {Line} from 'react-chartjs-2';

import Admin from './Admin.js'

import 'chartjs-plugin-colorschemes';
import './style.css';

const api = '/api/';
const REFRESH_MS = 30_000;

const timeDisplayFormats = {}

const ranges = [
    {
        value: -1,
        text: 'Auto',
        interval: 'moment'
    },
    {
        value: 60,
        text: 'Past hour',
        interval: 'minute',
        unit: 'minute'
    },
    {
        value: 6 * 60,
        text: 'Past 6 hours',
        interval: 'minute',
        unit: 'hour'
    },
    {
        value: 24 * 60,
        text: 'Past day',
        interval: 'minute',
        unit: 'hour'
    },
    {
        value: 7 * 24 * 60,
        text: 'Past week',
        interval: 'hour',
        unit: 'day'
    },
    {
        value: 31 * 24 * 60,
        text: 'Past month',
        interval: 'day',
        unit: 'week'
    },
    {
        value: 365 * 24 * 60,
        text: 'Past year',
        interval: 'day',
        unit: 'month'
    }
];

export default class App extends Component {
    chartRef = React.createRef();

    selectedSettings = {
        sourcesLeft: [],
        sourcesRight: [],
        rawElements: '',
        autoElements: true,
        retention: ranges[1]
    };

    state = {
        prePlotError: null,
        isFetching: false,
        isLoading: false,
        loadedSources: [],
        loadedSeries: [],
        selectedSettings: this.selectedSettings,
        plottedSettings: null,
        displayedSettings: null,
        datasets: []
    };
    leftAxisRef = React.createRef();
    rightAxisRef = React.createRef();

    limitInputRef = React.createRef();
    plotButton = React.createRef();

    handleErrors = (response) => {
        if (!response.ok) throw new Error(response.statusText);
        return response;
    };

    componentWillUnmount = () => {
        clearInterval(this.interval);
    };

    componentDidMount = () => {
        this.interval = setInterval(() => {
            this.loadData(true);
            this.fetchSources(true);
        }, REFRESH_MS);

        const chartContainer = document.getElementById('chart-wrapper');
        const dataCanvas = document.getElementById('data-chart');
        const cursorCanvas = document.getElementById('cursor');
        const cursorCtx = cursorCanvas.getContext('2d');

        chartContainer.onmousemove = (e) => {
            const dataWidth = parseInt(dataCanvas.style.width, 10);
            const dataHeight = parseInt(dataCanvas.style.height, 10);
            cursorCanvas.width = dataWidth;
            cursorCanvas.height = dataHeight;
            const {offsetLeft, offsetTop} = cursorCanvas;
            const {clientX, clientY} = e;
            cursorCtx.clearRect(0, 0, cursorCanvas.width, cursorCanvas.height);
            if (clientX <= offsetLeft + dataWidth && clientX >= 48 && clientY <= offsetTop + dataHeight && clientY >= offsetTop) {
                cursorCtx.beginPath();
                cursorCtx.moveTo(clientX - offsetLeft, this.state.datasets.length > 0 ? 32 : 10);
                cursorCtx.lineTo(clientX - offsetLeft, dataHeight - 30);
                cursorCtx.stroke();
            }
        };
        chartContainer.onmouseleave = () => {
            cursorCtx.clearRect(0, 0, cursorCanvas.width, cursorCanvas.height);
        };

        this.fetchSources(false);
    };

    createSource = (data, callback) => {
        console.log('creating source ' + data);
        fetch(`${api}source`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            },
            body: JSON.stringify(data)
        })
            .then(this.handleErrors)
            .then(() => this.fetchSources(true).then(() => callback(true)))
            .catch(() => this.fetchSources(true).then(() => callback(false)));
    };

    deleteSource = (source, callback) =>
        fetch(`${api}source/${encodeURIComponent(source)}`, {
            method: 'delete'
        })
            .then(this.handleErrors)
            .then(() => this.fetchSources(true).then(() => callback(true)))
            .catch(() => this.fetchSources(true).then(() => callback(false)));

    peekSource = (source, callback) =>
        fetch(`${api}peek/${encodeURIComponent(source)}`)
            .then(this.handleErrors)
            .then(response => response.json())
            .then(result => callback(true, result))
            .catch((e) => callback(false, e.message));

    syncSource = (source, history, callback) =>
        fetch(`${api}sync/${encodeURIComponent(source)}?history=${history}`, {
            method: 'POST'
        })
            .then(this.handleErrors)
            .then(result => callback(true, null))
            .catch((e) => callback(false, e.message));

    fetchSources = (background) => {

        if (!background) {
            this.setState({isFetching: true});
        }
        return fetch(`${api}source`)
            .then(this.handleErrors)
            .then(response => response.json())
            .then(results => {
                const loaded = results.flatMap(source =>
                    source.fields.map(field => ({
                        source: source,
                        datalabel: field.label
                    }))
                );
                const sources = Object.assign({}, ...loaded.map(entry => {
                    const source = entry.source;
                    const datalabel = entry.datalabel;
                    const id = source.url + ':' + datalabel;
                    return {
                        [id]: {
                            selected: false,
                            key: id,
                            datalabel: datalabel,
                            value: id,
                            text: source.name + ' [' + datalabel + ']',
                            source: source
                        }
                    }
                }));
                const {selectedSettings, plottedSettings} = this.state;

                const selectedLeft = selectedSettings.sourcesLeft.filter(source => source in sources);
                const selectedRight = selectedSettings.sourcesRight.filter(source => source in sources);

                let currentPlotted = plottedSettings;
                if (currentPlotted !== null) {
                    const plottedLeft = currentPlotted.sourcesLeft.filter(source => source in sources);
                    const plottedRight = currentPlotted.sourcesRight.filter(source => source in sources);
                    currentPlotted = {...currentPlotted, sourcesLeft: plottedLeft, sourcesRight: plottedRight}
                }

                this.leftAxisRef.current.setState({value: selectedLeft});
                this.rightAxisRef.current.setState({value: selectedRight});

                this.setState({
                    loadedSources: results,
                    loadedSeries: sources,
                    selectedSettings: {...selectedSettings, sourcesLeft: selectedLeft, sourcesRight: selectedRight},
                    plottedSettings: currentPlotted
                });
                if (!background) {
                    this.setState({isFetching: false});
                }
            })
            .catch((reason) => {
                console.log(reason);
            });
    };

    handlePlot = () => this.setState({plottedSettings: this.state.selectedSettings}, () => this.loadData(false));

    loadData = (background) => {
        const {plottedSettings, loadedSeries, prePlotError} = this.state;

        if (plottedSettings) {
            const {sourcesLeft, sourcesRight, retention, autoElements, rawElements} = plottedSettings;

            if (prePlotError == null) {
                const retentionMinutes = retention.value;
                const interval = autoElements ?  (retentionMinutes === -1 ? ranges[1].interval : retention.interval) : 'moment';

                if (!background) {
                    this.setState({isLoading: true, hasPlotted: true});
                }
                const timeRange = (retentionMinutes === -1 && rawElements)
                    ? ''
                    : `&from=${Date.now() - (retentionMinutes === -1 ? ranges[1].value : retentionMinutes) * 60 * 1000}&to=${Date.now()}`;
                const agg = autoElements ? 'average' : 'raw';
                const rawParam = rawElements ? `&raw_elements=${rawElements}` : ''
                Promise.all(
                    sourcesLeft.concat(sourcesRight).map(sourceId =>
                        fetch(`${api}query?source=${encodeURIComponent(sourceId)}${timeRange}&aggregation=${agg}&interval=${interval}${rawParam}`)
                            .then(this.handleErrors)
                            .then(response => response.json())
                            .then(results => {
                                const sourceRef = loadedSeries[sourceId];
                                return {
                                    label: sourceRef.text,
                                    yAxisID: sourcesRight.includes(sourceRef.key) ? 'y-axis-r' : 'y-axis-l',
                                    source: sourceRef,
                                    fill: false,
                                    borderJoinStyle: 'round',
                                    lineTension: 0,
                                    pointRadius: 3,
                                    pointHoverRadius: 6,
                                    data: results.map(source => {
                                        return {
                                            x: source.timestamp,
                                            y: source.value
                                        };
                                    })
                                };
                            })
                    )
                ).then(results => {
                    if (!background) {
                        this.setState({isLoading: false});
                    }
                    if (!background || _.isEqual(this.state.plottedSettings, plottedSettings)) {
                        this.setState({
                            datasets: results,
                            displayedSettings: plottedSettings
                        });
                    }
                }).catch((reason) => {
                    console.log(reason);
                });
            }
        }
    };

    handleAutoElementToggle = (event, data) => {
        if (!data.checked) {
            this.setState({selectedSettings: {...this.state.selectedSettings, autoElements: false}});
            setTimeout(() => {
                this.limitInputRef.current.focus();
            }, 0)
        } else {
            this.setState({selectedSettings: {...this.state.selectedSettings, rawElements: '', autoElements: true}});
        }
    };

    handleElementInput = (event, data) => {
        this.setState({selectedSettings: {...this.state.selectedSettings, rawElements: event.target.value}});
    };

    handleRangeSelect = (event, data) => {
        const retention = ranges.find(range => range.value === data.value);
        this.setState({selectedSettings: {...this.state.selectedSettings, retention: retention}});
    };

    handleLeftSourceSelect = (event, data) => {
        this.setState({selectedSettings: {...this.state.selectedSettings, sourcesLeft: data.value}});
    };

    handleRightSourceSelect = (event, data) => {
        this.setState({selectedSettings: {...this.state.selectedSettings, sourcesRight: data.value}});
    };

    isPlottable = () => {
        const {selectedSettings} = this.state;
        return (selectedSettings.autoElements || (selectedSettings.rawElements && util.isInt(selectedSettings.rawElements)));
    };

    render() {
        const {selectedSettings, displayedSettings, isFetching, isLoading, loadedSeries, loadedSources, datasets, prePlotError} = this.state;
        const {retention = {}, sourcesLeft, sourcesRight, autoElements} = displayedSettings || {};

        const minTime = (retention.value === -1 && !autoElements) ? null : Date.now() - (retention.value === -1 ? ranges[1].value : retention.value) * 60 * 1000
        const maxTime = Date.now()

        const minPoint = Math.min(datasets.map(set => set.data[0]))
        const maxPoint = Math.max(datasets.map(set => set.data[set.data.length - 1]))
        const maxDataWidth = (maxPoint ? maxPoint.x : 0) - (minPoint ? minPoint.x : 0);
        const unit = retention.unit
            ? retention.unit
            : maxDataWidth >= 1.5 * 30 * 24 * 60 * 60 * 1000 ? 'month'
                : maxDataWidth >= 10 * 24 * 60 * 60 * 1000 ? 'week'
                    : maxDataWidth >= 24 * 60 * 60 * 1000 ? 'day'
                        : maxDataWidth >= 2 * 60 * 60 * 1000 ? 'hour'
                            : 'minute';

        const options = {
            animation: {
                duration: 0
            },
            plugins: {
                colorschemes: {
                    scheme: 'office.Berlin6'
                }
            },
            responsive: true,
            tooltips: {
                intersect: false,
                axis: 'x'
            },
            hover: {
                mode: 'nearest',
                intersect: false,
                axis: 'x',
                animationDuration: 100
            },
            scales: {
                yAxes: [
                    {
                        id: 'y-axis-l',
                        position: 'left',
                        ticks: {
                            display: sourcesLeft && sourcesLeft.length
                        }
                    },
                    {
                        id: 'y-axis-r',
                        position: 'right',
                        ticks: {
                            display: sourcesRight && sourcesRight.length
                        }
                    }
                ],
                xAxes: [
                    {
                        type: 'time',
                        bounds: 'ticks',
                        time: {
                            displayFormats: timeDisplayFormats,
                            unit: unit
                        },
                        ticks: {
                            maxTicksLimit: retention.value ? 12 : -1,
                            autoSkip: true,
                            min: minTime,
                            max: maxTime
                        },
                        distribution: 'linear'
                    }
                ]
            }
        };

        return (
            <div className='container'>
                <span className='datasource-select'>
                    <Dropdown
                        ref={this.leftAxisRef}
                        className='left-datasource-dropdown'
                        placeholder='Left axis data source'
                        fluid
                        multiple
                        search
                        selection
                        loading={isFetching || isLoading}
                        disabled={isFetching || isLoading}
                        options={Object.values(loadedSeries).filter(src => !selectedSettings.sourcesRight.includes(src.key))}
                        onChange={this.handleLeftSourceSelect}
                    />
                    <Dropdown
                        ref={this.rightAxisRef}
                        className='right-datasource-dropdown'
                        placeholder='Right axis data source'
                        fluid
                        multiple
                        search
                        selection
                        loading={isFetching || isLoading}
                        disabled={isFetching || isLoading}
                        options={Object.values(loadedSeries).filter(src => !selectedSettings.sourcesLeft.includes(src.key))}
                        onChange={this.handleRightSourceSelect}
                    />
                </span>
                <span className='range-select'>
                <Popup
                    position='top center'
                    trigger={<Header as='h4' textAlign='center' className='helpable-text'>Time period</Header>}
                >
                    The time range to limit queries to. If set to <strong>Auto</strong>, the time range will be automatically selected to fit all queried elements.
                    However, if <strong>Element count</strong> is also set to <strong>Auto</strong>, this option will select elements from the past hour.
                </Popup>

                    <Dropdown
                        placeholder='Range'
                        fluid
                        selection
                        loading={isLoading}
                        disabled={isLoading}
                        options={ranges}
                        className='range-dropdown'
                        onChange={this.handleRangeSelect}
                        defaultValue={ranges[1].value}
                    />
                </span>

                <span className='element-limit-select'>
                    <Popup
                        position='top center'
                        trigger={<Header as='h4' textAlign='center' className='helpable-text'>Element count</Header>}
                    >
                        The number of elements to query. If set to <strong>Auto</strong>, elements will be limited to the provided <strong>Time period</strong> and aggregated & displayed automatically.
                        If not, the number of elements to query must be explicitly provided.
                    </Popup>
                    <Grid className='element-limit'>
                    <Checkbox
                        label='Auto'
                        onChange={this.handleAutoElementToggle}
                        className='element-limit-auto'
                        checked={selectedSettings.autoElements}
                    />
                    <Input
                        ref={this.limitInputRef}
                        className='element-limit-input'
                        fluid
                        onChange={this.handleElementInput}
                        value={selectedSettings.rawElements}
                        error={!!selectedSettings.rawElements && !util.isInt(selectedSettings.rawElements)}
                        disabled={selectedSettings.autoElements}
                        placeholder='Limit'
                        onKeyPress={e => {
                            if (e.charCode === 13) {
                                this.plotButton.current.handleClick(e);
                            }
                        }}
                    />
                    </Grid>
                </span>

                <span className='info-text'>
                    {prePlotError}
                </span>
                <span className='admin-launcher'>

                    <Button
                        ref={this.plotButton}
                        loading={isFetching || isLoading}
                        disabled={!this.isPlottable() || isFetching || isLoading || prePlotError != null}
                        onClick={this.handlePlot}
                    >
                        Plot
                    </Button>

                    <Admin sources={loadedSources}
                           peekSource={(source, callback) => this.peekSource(source, callback)}
                           onDelete={(source, callback) => this.deleteSource(source, callback)}
                           onCreate={(source, callback) => this.createSource(source, callback)}
                           onSync={(source, history, callback) => this.syncSource(source, history, callback)}
                    />
                </span>
                <br style={{clear: 'both'}}/>
                <div className='chart-container' id='chart-wrapper'>
                    <canvas id='cursor'/>
                    <Line ref={this.chartRef} id='data-chart' data={{datasets: datasets}} options={options} redraw={false}/>
                </div>
            </div>
        );
    }
}
