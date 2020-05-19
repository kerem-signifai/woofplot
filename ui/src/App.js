import React, {Component} from 'react';
import {Button, Dropdown} from 'semantic-ui-react';
import {Line} from 'react-chartjs-2';

import Admin from './Admin.js'

import 'chartjs-plugin-colorschemes';
import './style.css';

const api = '/api/';
const REFRESH_MS = 3000;

export default class App extends Component {
    chartRef = React.createRef();

    selectedSettings = {
        sourcesLeft: [],
        sourcesRight: [],
        retentionMinutes: 60
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
                "Content-Type": "application/json",
                "Accept": "application/json"
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
            const {sourcesLeft, sourcesRight, retentionMinutes} = plottedSettings;

            if (prePlotError == null) {
                const aggIntervals = [
                    {
                        geq: 6 * 30 * 24 * 60,
                        interval: 'week'
                    },
                    {
                        geq: 30 * 24 * 60,
                        interval: 'day'
                    },
                    {
                        geq: 7 * 24 * 60,
                        interval: 'hour'
                    },
                    {
                        geq: 0,
                        interval: 'minute'
                    }
                ];

                const interval = aggIntervals.find(cfg => retentionMinutes >= cfg.geq).interval;

                if (!background) {
                    this.setState({isLoading: true, hasPlotted: true});
                }
                Promise.all(
                    sourcesLeft.concat(sourcesRight).map(sourceId =>
                        fetch(`${api}query?source=${encodeURIComponent(sourceId)}&from=${Date.now() - retentionMinutes * 60 * 1000}&to=${Date.now()}&aggregation=average&interval=${interval}`)
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
                    if (!background || this.state.plottedSettings === plottedSettings) {
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

    handleRangeSelect = (event, data) => {
        this.setState({selectedSettings: {...this.state.selectedSettings, retentionMinutes: data.value}});
    };

    handleLeftSourceSelect = (event, data) => {
        this.setState({selectedSettings: {...this.state.selectedSettings, sourcesLeft: data.value}});
    };

    handleRightSourceSelect = (event, data) => {
        this.setState({selectedSettings: {...this.state.selectedSettings, sourcesRight: data.value}});
    };

    render() {
        const {selectedSettings, plottedSettings, displayedSettings, isFetching, isLoading, loadedSeries, loadedSources, datasets, prePlotError} = this.state;
        const {sourcesLeft, sourcesRight} = plottedSettings || {};
        const {retentionMinutes = 0} = displayedSettings || {};
        const dateFormats = [
            {
                geq: 6 * 30 * 24 * 60,
                unit: 'month'
            },
            {
                geq: 3 * 24 * 60,
                unit: 'day'
            },
            {
                geq: 0,
                unit: 'hour'
            }
        ];

        const format = dateFormats.find(cfg => retentionMinutes >= cfg.geq);

        const customFormat = ('formatStr' in format) ? {[format.unit]: format.formatStr} : {};

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
                            displayFormats: customFormat,
                            unit: format.unit
                        },
                        ticks: {
                            maxTicksLimit: 12,
                            autoSkip: true,
                            min: Date.now() - (retentionMinutes * 60 * 1000),
                            max: Date.now()
                        },
                        distribution: 'linear'
                    }
                ]
            }
        };

        const ranges = [
            {
                value: 60,
                text: 'Past hour'
            },
            {
                value: 6 * 60,
                text: 'Past 6 hours'
            },
            {
                value: 24 * 60,
                text: 'Past day'
            },
            {
                value: 7 * 24 * 60,
                text: 'Past week'
            },
            {
                value: 31 * 24 * 60,
                text: 'Past month'
            },
            {
                value: 365 * 24 * 60,
                text: 'Past year'
            }
        ];

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
                    <Dropdown
                        placeholder='Range'
                        fluid
                        selection
                        loading={isLoading}
                        disabled={isLoading}
                        options={ranges}
                        onChange={this.handleRangeSelect}
                        defaultValue={ranges[0].value}
                    />
                </span>
                <span className='plot-button'>
                    <Button
                        loading={isFetching || isLoading}
                        disabled={isFetching || isLoading || prePlotError != null}
                        onClick={this.handlePlot}
                    >
                        Plot
                    </Button>
                </span>
                <span className='info-text'>
                    {prePlotError}
                </span>

                <span className='admin-launcher'>
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
