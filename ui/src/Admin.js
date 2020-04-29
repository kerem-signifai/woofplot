import React, {Component} from 'react'
import {
    Button,
    Form,
    Grid,
    Header,
    Icon,
    List,
    Message,
    Modal,
    Popup,
    Segment,
    Table
} from 'semantic-ui-react'

const DEFAULT_SYNC_HISTORY = 10;

export default class Admin extends Component {

    state = {
        creating: false,
        pendingDeletions: [],
        selectedUrl: null,
        peekLoading: false,
        peekUrl: null,
        peekError: null,
        peekPayload: null,
        selectedElements: [],
        clickedElement: null,
        elementLabel: null,
        hoveredSelected: null,
        inputName: null,
        syncClicked: false,
        syncLoading: false,
        syncHistory: DEFAULT_SYNC_HISTORY,
        syncError: null,
        syncSuccess: false,
        createLoading: false,
        createError: null
    };

    adminPanelClosed = () => {
        this.setState({
            creating: false,
            peekLoading: false,
            selectedUrl: null,
            peekUrl: null,
            peekError: null,
            peekPayload: null,
            selectedElements: [],
            clickedElement: null,
            elementLabel: null,
            hoveredSelected: null,
            inputName: null,
            syncClicked: false,
            syncLoading: false,
            syncHistory: 10,
            syncError: null,
            syncSuccess: false,
            createLoading: false,
            createError: null
        });
    };

    openCreator = () =>
        this.setState({
            creating: true,
            selectedUrl: null,
            peekLoading: false,
            peekPayload: null,
            peekUrl: null,
            peekError: null,
            selectedElements: [],
            clickedElement: null,
            elementLabel: null,
            hoveredSelected: null,
            inputName: null,
            syncClicked: false,
            syncLoading: false,
            syncHistory: 10,
            syncError: null,
            syncSuccess: false,
            createLoading: false,
            createError: null
        });

    closeCreator = () =>
        this.setState({
            creating: false,
            selectedUrl: null,
            peekLoading: false,
            peekPayload: null,
            peekUrl: null,
            peekError: null,
            selectedElements: [],
            clickedElement: null,
            elementLabel: null,
            hoveredSelected: null,
            inputName: null,
            syncClicked: false,
            syncLoading: false,
            syncHistory: 10,
            syncError: null,
            syncSuccess: false,
            createLoading: false,
            createError: null
        });

    handleUrlInput = e => {
        this.setState({peekUrl: e.target.value})
    };

    handlePeekResponse = (success, res) => {
        if (this.state.peekLoading) {
            this.setState({
                peekLoading: false,
                peekPayload: success ? res : null,
                peekError: success ? null : res,
                selectedElements: [],
                clickedElement: null,
                elementLabel: null,
                hoveredSelected: null,
                inputName: null,
                createLoading: false,
                createError: null
            });
        }
    };

    handleSyncClicked = (source) => this.setState({
        syncClicked: true,
        syncSuccess: false,
        syncError: null
    });

    handleSyncClosed = () => this.setState({
        syncClicked: false,
        syncHistory: DEFAULT_SYNC_HISTORY,
        syncSuccess: false,
        syncError: null
    });

    handleHistorySet = (e) => {
        this.setState({syncHistory: e.target.value})
    };

    dispatchSync = (sourceId) => {
        this.setState({syncLoading: true});
        this.props.onSync(sourceId, this.state.syncHistory, (success, error) => {
            this.setState({
                syncLoading: false,
                syncSuccess: success,
                syncError: success ? null : error
            });
        });
    };

    handleElementSelect = (idx) => {
        this.setState({clickedElement: idx, elementLabel: null});
    };

    handleHoverSelected = (idx, hovering) => {
        if (hovering) {
            this.setState({hoveredSelected: idx});
        } else {
            this.setState({hoveredSelected: null});
        }
    };

    handleElementAdd = () => {
        const {elementLabel, selectedElements, clickedElement} = this.state;
        this.setState({
            selectedElements: [...selectedElements, {
                label: elementLabel,
                idx: clickedElement
            }].sort((a, b) => a.idx - b.idx),
            clickedElement: null,
            elementLabel: null
        })
    };
    handleElementRemove = (idx) => this.setState({selectedElements: this.state.selectedElements.filter(e => e.idx !== idx)});

    handleElementLabelChange = (e) => this.setState({elementLabel: e.target.value});

    handleNameChange = (e) => this.setState({inputName: e.target.value});

    handleCreate = (e) => {
        const {inputName, selectedUrl, selectedElements, peekPayload} = this.state;
        this.setState({createLoading: true});
        let rex = `^`;

        if (peekPayload.typ === 'NUMERIC') {
            rex += `(.*?)`
        } else {
            const text = peekPayload.text;
            const numColons = text.split(':').length - 1;
            const numSpaces = text.split(' ').length - 1;
            const parts = Math.max(numColons, numSpaces);
            const delim = (parts === numColons) ? ':' : ' ';

            let lastIdx = -1;
            selectedElements.forEach(element => {
                const idx = element.idx;
                if (lastIdx !== idx && idx - lastIdx !== 1) {
                    rex += `(?:[^${delim}]*${delim}){${idx - lastIdx - 1}}`
                }
                rex += '(.*?)';
                if (parts !== idx) {
                    rex += `${delim}`;
                }
                lastIdx = idx;
            });
            if (lastIdx !== parts) {
                rex += `.*?`
            }
        }

        rex += '$';
        const data = {
            url: selectedUrl,
            name: inputName,
            dataLabels: selectedElements.map(element => element.label),
            pattern: rex

        };
        this.props.onCreate(data, (success) => {
            if (success) {
                this.closeCreator();
            } else {
                this.setState({
                    createLoading: false,
                    createError: 'Failed to create source'
                });
            }
        });
    };

    dispatchPeek = () => {
        this.setState({peekLoading: true, peekPayload: null, peekError: null, selectedUrl: this.state.peekUrl});
        this.props.peekSource(this.state.peekUrl.trim(), this.handlePeekResponse);
    };

    renderPreview = () => {
        const {peekPayload, peekError, selectedElements, hoveredSelected, inputName, createLoading, createError} = this.state;
        if (peekPayload != null) {
            const preview = peekPayload.typ === 'NUMERIC' ? this.renderNumPreview() : this.renderTextPreview();
            return (
                <Segment attached='top'>
                    <Header textAlign='center'>Example Response</Header>
                    <div className='preview-container'>
                        {preview}
                    </div>
                    <Grid columns={2} stackable padded relaxed='very'>
                        <Grid.Column floated='right'>
                            <Header textAlign='center' className='peek-list-header'>Selected Elements</Header>
                            <Segment basic textAlign='center' className='selected-container'>
                                {
                                    selectedElements.length === 0
                                        ?
                                        <span>No elements selected</span>
                                        :
                                        <List ordered className='peek-list'>
                                            {
                                                selectedElements.map(element =>
                                                    <List.Item key={element.idx} className='peek-list-element-wrapper'>
                                                            <span
                                                                onMouseEnter={() => this.handleHoverSelected(element.idx, true)}
                                                                onMouseLeave={() => this.handleHoverSelected(element.idx, false)}
                                                                onClick={() => this.handleElementRemove(element.idx)}
                                                                className={'peek-list-element' + (element.idx === hoveredSelected ? ' peek-list-element-over' : '')}>{element.label}
                                                            </span>
                                                    </List.Item>
                                                )}
                                        </List>
                                }
                            </Segment>
                        </Grid.Column>

                        <Grid.Column floated='right'>

                            <Form onSubmit={this.handleCreate}>
                                <Form.Input
                                    action={
                                        <Popup disabled={selectedElements.length > 0 && !!inputName}
                                               className='error-popup'
                                               on='hover'
                                               trigger={
                                                   <div className='right-action-wrapper'>
                                                       <Button
                                                           className='right-action'
                                                           content='Create'
                                                           type='submit'
                                                           icon='signup'
                                                           loading={createLoading}
                                                           disabled={selectedElements.length === 0 || !inputName || createLoading}
                                                       />
                                                   </div>
                                               }>
                                            <Popup.Content>
                                                {selectedElements.length === 0 ? 'Select at least one element' : 'Enter source name'}
                                            </Popup.Content>

                                        </Popup>
                                    }
                                    className='left-input'
                                    onChange={this.handleNameChange}
                                    placeholder='Name'
                                    fluid
                                />
                            </Form>
                            {createError != null
                                ?
                                <Message negative>
                                    <Message.Header>Failed to create source</Message.Header>
                                    <p>{createError}</p>
                                </Message>
                                : null}
                        </Grid.Column>
                    </Grid>
                </Segment>
            );
        } else if (peekError != null) {
            return (
                <Message negative>
                    <Message.Header>Failed to load data</Message.Header>
                    <p>Ensure the provided source URL is valid.</p>
                </Message>
            );
        }
    };

    renderSelectedElement = (idx, entry) => {
        const {hoveredSelected} = this.state;

        return <span
            id={idx}
            onMouseEnter={() => this.handleHoverSelected(idx, true)}
            onMouseLeave={() => this.handleHoverSelected(idx, false)}
            onClick={() => this.handleElementRemove(idx)}
            className={'peek-element peek-element-selected noselect' + (idx === hoveredSelected ? ' peek-list-element-over' : '')}
            key={idx + '_elem'}
        >
            {entry}
        </span>;
    };

    renderDisabledElement = (idx, entry) =>
        <span
            id={idx}
            onMouseEnter={() => this.handleHoverSelected(idx, true)}
            onMouseLeave={() => this.handleHoverSelected(idx, false)}
            onClick={() => this.handleElementSelect(idx)}
            className={'peek-element-disabled noselect'}
            key={idx + '_elem'}>{entry}
        </span>;

    renderUnselectedElement = (idx, entry) => {
        const {clickedElement, elementLabel} = this.state;
        const curSelected = idx === clickedElement;

        return <Popup
            className='noselect'
            open={curSelected}
            onClose={() => this.handleElementSelect(null)}
            key={idx + '_popup'}
            on={'click'}
            trigger={
                <span
                    id={idx}
                    onMouseEnter={() => this.handleHoverSelected(idx, true)}
                    onMouseLeave={() => this.handleHoverSelected(idx, false)}
                    onClick={() => this.handleElementSelect(idx)}
                    className={'peek-element peek-element-unselected noselect'}
                    key={idx + '_elem'}
                >
                    {entry}
                </span>
            }
            content={
                <Form onSubmit={this.handleElementAdd}>
                    <Form.Input
                        action={{
                            content: 'Add',
                            disabled: !elementLabel
                        }}
                        onChange={this.handleElementLabelChange}
                        placeholder='Label'
                        autoFocus
                    />
                </Form>

            }
        />
    };

    isFloat = (val) => {
        const floatRegex = /^-?\d+(?:[.,]\d*?)?$/;
        if (!floatRegex.test(val)) {
            return false;
        }

        return !isNaN(parseFloat(val));
    };

    isInt = (val) => {
        const intRegex = /^\d+?$/;
        if (!intRegex.test(val)) {
            return false;
        }

        return !isNaN(parseInt(val));
    };

    renderTextPreview = () => {
        const {selectedElements} = this.state;
        const text = this.state.peekPayload.text;
        const numColons = text.split(':').length - 1;
        const numSpaces = text.split(' ').length - 1;
        const delim = numColons > numSpaces ? ':' : ' ';

        let entries = [];
        text.split(delim).forEach(entry => {
            if (entries.length !== 0) {
                entries.push(<span className='noselect breakable' key={entries.length + '_delim'}>{delim}</span>);
            }
            const idx = entries.length / 2;
            const curAdded = selectedElements.map(a => a.idx).includes(idx);

            if (!this.isFloat(entry)) {
                entries.push(this.renderDisabledElement(idx, entry));
            } else {
                entries.push(curAdded ? this.renderSelectedElement(idx, entry) : this.renderUnselectedElement(idx, entry));
            }
        });

        return entries
    };

    renderNumPreview = () => {
        const {selectedElements} = this.state;
        const num = this.state.peekPayload.number;
        const curAdded = selectedElements.length === 1 && selectedElements[0].idx === 0;

        return curAdded ? this.renderSelectedElement(0, num) : this.renderUnselectedElement(0, num)
    };


    renderSourceBuilder = () => {
        const {creating, peekLoading, peekUrl} = this.state;
        if (creating) {
            const peekPreview = this.renderPreview();
            return (
                <Table.Row key='creator'>
                    <Table.Cell colSpan='5'>
                        <Segment className='creator-segment'>
                            <Header textAlign='center'>Create Source</Header>
                            <Grid>
                                <Grid.Row centered>
                                    <Form onSubmit={this.dispatchPeek}>
                                        <Form.Input
                                            action={
                                                <Popup
                                                    disabled={!!peekUrl}
                                                    className='error-popup'
                                                    on='hover'
                                                    trigger={
                                                        <div className='right-action-wrapper'>
                                                            <Button
                                                                className='right-action'
                                                                disabled={peekLoading || !peekUrl}
                                                                loading={peekLoading}
                                                                icon
                                                                onClick={this.dispatchPeek}
                                                            >
                                                                <Icon name='search'/>
                                                            </Button>
                                                        </div>
                                                    }
                                                >
                                                    <Popup.Content>
                                                        Enter a valid URL
                                                    </Popup.Content>

                                                </Popup>
                                            }
                                            className='url-input left-input'
                                            placeholder='URL'
                                            onChange={this.handleUrlInput}
                                            autoFocus
                                        />
                                    </Form>
                                </Grid.Row>
                            </Grid>
                            {peekPreview}

                        </Segment>
                    </Table.Cell>
                </Table.Row>
            )
        }
    };

    dispatchDelete = (sourceId) => {
        const pendingDeletions = this.state.pendingDeletions.slice();
        pendingDeletions.push(sourceId);
        this.setState({pendingDeletions: pendingDeletions});
        this.props.onDelete(sourceId, (success) => {
            this.setState({pendingDeletions: this.state.pendingDeletions.filter(a => a !== sourceId)});
        });
    };

    render() {
        const {creating, pendingDeletions, syncLoading, syncError, syncSuccess, syncClicked, syncHistory} = this.state;
        const sources = this.props.sources;
        const editRow = this.renderSourceBuilder();
        return (
            <Modal
                dimmer='inverted'
                onClose={this.adminPanelClosed}
                centered={false}
                size='fullscreen'
                trigger={<Button icon><Icon name='setting'/></Button>}>
                <Modal.Header>Manage Sources</Modal.Header>
                <Modal.Content image>
                    <Modal.Description>
                        <Table celled>
                            <Table.Header>
                                <Table.Row>
                                    <Table.HeaderCell textAlign='center'>Name</Table.HeaderCell>
                                    <Table.HeaderCell textAlign='center'>Data Types</Table.HeaderCell>
                                    <Table.HeaderCell textAlign='center'>Data Pattern</Table.HeaderCell>
                                    <Table.HeaderCell textAlign='center'>URL</Table.HeaderCell>
                                    <Table.HeaderCell width='1'/>
                                </Table.Row>
                            </Table.Header>
                            <Table.Body>
                                {sources.map(source => {
                                    return (
                                        <Table.Row key={source.url}>
                                            <Table.Cell textAlign='center'>
                                                <div>{source.name}</div>
                                            </Table.Cell>
                                            <Table.Cell>
                                                <List ordered>
                                                    {source.dataLabels.map(dataLabel => (
                                                        <List.Item key={source.url + '_' + dataLabel}>
                                                            {dataLabel}
                                                        </List.Item>
                                                    ))}
                                                </List>
                                            </Table.Cell>
                                            <Table.Cell textAlign='center'>
                                                <div><code>{source.pattern}</code></div>
                                            </Table.Cell>
                                            <Table.Cell textAlign='center'>
                                                <div><code>{source.url}</code></div>
                                            </Table.Cell>
                                            <Table.Cell textAlign='center' className='action-cell'>

                                                <Popup
                                                    className='noselect'
                                                    // open={syncClicked}
                                                    key={source.url}
                                                    position='top center'
                                                    onClose={() => this.handleSyncClosed()}
                                                    on={'click'}
                                                    trigger={
                                                        <div className='right-action-wrapper'>
                                                        <Button
                                                            loading={syncLoading}
                                                            disabled={syncLoading}
                                                            icon
                                                            onClick={() => this.handleSyncClicked(source.url)}
                                                            className='source-action-button'
                                                        >
                                                            <Icon name='redo alternate'/>
                                                        </Button>
                                                        </div>

                                                    }
                                                    content={
                                                        <div>
                                                        <Form onSubmit={() => this.dispatchSync(source.url)}>
                                                            <Form.Field>
                                                                <label>How many recent events to load:</label>
                                                            <Form.Input
                                                                action={{
                                                                    content: 'Load',
                                                                    disabled: syncLoading || !syncHistory || !this.isInt(syncHistory),
                                                                    loading: syncLoading
                                                                }}
                                                                disabled={syncLoading}

                                                                onChange={this.handleHistorySet}
                                                                placeholder='History'
                                                                defaultValue={DEFAULT_SYNC_HISTORY}
                                                                autoFocus
                                                            />
                                                            </Form.Field>

                                                        </Form>
                                                            {syncError != null ?
                                                                <Message negative>
                                                                    <p>{syncError}</p>
                                                                </Message>
                                                                : (syncSuccess ?
                                                                        <Message positive>
                                                                            <p>Recent history loaded</p>
                                                                        </Message>
                                                                        : null
                                                                )}
                                                        </div>
                                                    }
                                                />

                                                <Button
                                                    loading={pendingDeletions.includes(source.url)}
                                                    disabled={pendingDeletions.includes(source.url)}
                                                    icon
                                                    negative
                                                    onClick={() => this.dispatchDelete(source.url)}
                                                    className='source-action-button'
                                                >
                                                    <Icon name='trash alternate'/>
                                                </Button>
                                            </Table.Cell>
                                        </Table.Row>
                                    );
                                })}

                                {editRow}

                            </Table.Body>
                            <Table.Footer fullWidth>
                                <Table.Row>
                                    <Table.HeaderCell colSpan='6'>
                                        {
                                            creating ?
                                                <Button
                                                    floated='right'
                                                    icon
                                                    labelPosition='left'
                                                    primary
                                                    size='small'
                                                    onClick={this.closeCreator}
                                                >
                                                    <Icon name='times'/> Cancel
                                                </Button>
                                                :
                                                <Button
                                                    floated='right'
                                                    icon
                                                    labelPosition='left'
                                                    primary
                                                    size='small'
                                                    onClick={this.openCreator}
                                                >
                                                    <Icon name='plus'/> Add Source
                                                </Button>}
                                    </Table.HeaderCell>
                                </Table.Row>
                            </Table.Footer>
                        </Table>
                    </Modal.Description>
                </Modal.Content>
            </Modal>
        );
    }
}
